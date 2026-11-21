import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const mailpitUrl = "http://127.0.0.1:18025";
const customerEmail = `playwright-${Date.now()}@example.com`;

interface OperatorActionLinks {
  confirm: string;
  decline: string;
}

async function createBooking(page: Page, email: string, name: string): Promise<number> {
  await page.goto("/safari-tours");
  await page.getByRole("link", { name: "Details" }).first().click();
  await expect(page.getByRole("heading", { name: "Request Serengeti Great Migration" })).toBeVisible();
  await page.getByLabel("Departure date").selectOption({ index: 1 });
  await page.getByRole("spinbutton", { name: "Guests" }).fill("1");
  await page.getByLabel("Full name").fill(name);
  await page.getByLabel("Email").fill(email);
  await page.getByRole("button", { name: "Request booking" }).click();

  await expect(page.getByRole("status")).toContainText("Booking request #");
  const notice = await page.getByRole("status").innerText();
  const bookingId = notice.match(/Booking request #(\d+)/)?.[1];
  expect(bookingId, "booking request notice includes its ID").toBeTruthy();
  return Number(bookingId);
}

async function getOperatorActionLinks(
  request: APIRequestContext,
  bookingId: number
): Promise<OperatorActionLinks | null> {
  const response = await request.get(`${mailpitUrl}/api/v1/messages?limit=100`);
  expect(response.ok()).toBeTruthy();
  const mailbox = await response.json() as {
    messages: Array<{ ID: string; Subject: string; To: unknown }>;
  };
  const message = mailbox.messages.find((item) =>
    item.Subject === `Safari Availability Request - Booking #${bookingId}`);
  if (!message) return null;

  const detailsResponse = await request.get(`${mailpitUrl}/api/v1/message/${encodeURIComponent(message.ID)}`);
  expect(detailsResponse.ok()).toBeTruthy();
  const details = await detailsResponse.json() as { Text?: string };
  const extract = (label: string) => details.Text
    ?.match(new RegExp(`${label}:\\s*(https?:\\/\\/[^\\s]+)`, "i"))?.[1]
    ?.replace(/[),.]+$/, "");
  const confirm = extract("Confirm availability");
  const decline = extract("Decline request");
  return confirm && decline ? { confirm, decline } : null;
}

async function waitForOperatorActionLinks(
  request: APIRequestContext,
  bookingId: number
): Promise<OperatorActionLinks> {
  let links: OperatorActionLinks | null = null;
  await expect.poll(async () => {
    links = await getOperatorActionLinks(request, bookingId);
    return links ? "received" : "";
  }, { message: `operator email for booking #${bookingId} arrives in Mailpit`, timeout: 20_000 })
    .toBe("received");
  if (!links) throw new Error(`Operator email for booking #${bookingId} did not contain both action links.`);
  return links;
}

async function enquiryEmailReceived(request: APIRequestContext, email: string): Promise<boolean> {
  const response = await request.get(`${mailpitUrl}/api/v1/messages?limit=100`);
  if (!response.ok()) return false;
  const mailbox = await response.json() as {
    messages: Array<{ ID: string; Subject: string }>;
  };
  const candidates = mailbox.messages.filter((message) =>
    message.Subject.startsWith("Website enquiry |"));
  for (const message of candidates) {
    const detailsResponse = await request.get(
      `${mailpitUrl}/api/v1/message/${encodeURIComponent(message.ID)}`);
    if (!detailsResponse.ok()) continue;
    const details = await detailsResponse.json() as { Text?: string };
    if (details.Text?.includes(email)) return true;
  }
  return false;
}

async function openOperatorLink(page: Page, link: string) {
  const url = new URL(link);
  await page.goto(`${url.pathname}${url.search}`);
}

async function getVerificationCode(request: APIRequestContext, email: string): Promise<string> {
  const response = await request.get(`${mailpitUrl}/api/v1/messages?limit=50`);
  expect(response.ok()).toBeTruthy();
  const mailbox = await response.json() as {
    messages: Array<{ ID: string; Subject: string; To: unknown }>;
  };
  const message = mailbox.messages.find((item) =>
    item.Subject === "Your Wildpath Safaris sign-in code"
      && JSON.stringify(item.To).toLowerCase().includes(email.toLowerCase()));
  if (!message) return "";

  const detailsResponse = await request.get(`${mailpitUrl}/api/v1/message/${encodeURIComponent(message.ID)}`);
  expect(detailsResponse.ok()).toBeTruthy();
  const details = await detailsResponse.json() as { Text?: string; HTML?: string };
  return `${details.Text ?? ""} ${details.HTML ?? ""}`.match(/\b\d{6}\b/)?.[0] ?? "";
}

async function signInAsAdmin(request: APIRequestContext, apiUrl: string) {
  const email = "e2e-admin@example.com";
  const sendResponse = await request.post(`${apiUrl}/auth/send-code`, { data: { email } });
  expect(sendResponse.status()).toBe(204);
  let code = "";
  await expect.poll(async () => {
    code = await getVerificationCode(request, email);
    return code;
  }, { message: "admin verification email arrives in Mailpit", timeout: 20_000 })
    .toMatch(/^\d{6}$/);
  const verifyResponse = await request.post(`${apiUrl}/auth/verify-code`, {
    data: { email, code },
  });
  expect(verifyResponse.ok()).toBeTruthy();
  expect(await verifyResponse.json()).toMatchObject({ role: "ADMIN" });
}

test("unknown API endpoints return 404 instead of 500", async ({ request }) => {
  const response = await request.get("http://127.0.0.1:18080/api/bookings/999999");
  expect(response.status()).toBe(404);
});

test("customer can enquire about a tour without creating a booking", async ({ page, request }) => {
  const email = `enquiry-${Date.now()}@example.com`;
  const bookingRequests: string[] = [];
  page.on("request", (pageRequest) => {
    if (/\/api\/bookings(?:\/|$)/.test(new URL(pageRequest.url()).pathname)) {
      bookingRequests.push(pageRequest.url());
    }
  });

  await page.goto("/safari-tours");
  await page.getByRole("link", { name: "Details" }).first().click();
  await expect(page.getByRole("heading", { name: "Request Serengeti Great Migration" })).toBeVisible();
  await expect(page.locator(".enquiry-prompt"))
    .toContainText("Bookings require operator confirmation. Sign in later to check your booking status, or Enquire first");
  await page.getByRole("button", { name: "Enquire first" }).click();
  await page.getByLabel("Your name").fill("Enquiry Test Traveller");
  await page.getByLabel("Your email").fill(email);
  await page.getByLabel("Your question").fill("Could you advise on suitable travel dates?");
  await page.getByRole("button", { name: "Send enquiry" }).click();

  await expect(page.locator(".enquiry-widget").getByRole("status"))
    .toContainText("No booking has been created");
  await expect.poll(
    () => enquiryEmailReceived(request, email),
    { message: "operator receives the enquiry email", timeout: 20_000 }
  ).toBe(true);
  expect(bookingRequests).toHaveLength(0);
});

async function signInWithEmailCode(page: Page, request: APIRequestContext, email: string) {
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.getByLabel("Email address").fill(email);
  await page.getByRole("button", { name: "Send verification code" }).click();

  let code = "";
  await expect.poll(async () => {
    code = await getVerificationCode(request, email);
    return code;
  }, { message: "verification email arrives in the isolated Mailpit inbox", timeout: 20_000 }).toMatch(/^\d{6}$/);

  await page.getByLabel("Verification code").fill(code);
  await page.getByRole("button", { name: "Verify and sign in" }).click();
}

test("customer can book a tour, sign in, and use the mobile menu without initiating payment", async ({ page, request }) => {
  const paymentRequests: string[] = [];
  page.on("request", (browserRequest) => {
    if (browserRequest.url().includes("/api/payments/")) paymentRequests.push(browserRequest.url());
  });

  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto("/safari-tours");
  await expect(page.getByRole("heading", { name: "Choose your adventure." })).toBeVisible();
  const hitResponse = await page.evaluate(async () => {
    const response = await fetch("/api/hits", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pagePath: "/e2e-hit-counter" }),
    });
    return { status: response.status, body: await response.json() as { totalCount: number } };
  });
  expect(hitResponse.status).toBe(200);
  expect(hitResponse.body.totalCount).toBeGreaterThan(0);
  await createBooking(page, customerEmail, "Playwright Traveller");
  await expect(page.getByRole("status")).toContainText("Booking request #");

  await signInWithEmailCode(page, request, customerEmail);
  await page.getByRole("link", { name: "My account" }).click();
  await expect(page.getByRole("heading", { name: "Safari tour bookings" })).toBeVisible();
  await expect(page.getByRole("table").getByText("Serengeti Great Migration")).toBeVisible();
  await expect(page.getByRole("table").getByText("PENDING")).toBeVisible();
  expect(paymentRequests).toEqual([]);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Open navigation menu" }).click();
  const navigation = page.getByRole("navigation", { name: "Main navigation" });
  await expect(navigation.getByRole("link", { name: "Safari tours" })).toBeVisible();
  await navigation.getByRole("link", { name: "Safari tours" }).click();
  await expect(page).toHaveURL(/\/safari-tours$/);
  await expect(page.getByRole("button", { name: "Open navigation menu" })).toHaveAttribute("aria-expanded", "false");
});

test("customer can select extras and gets the correct total", async ({ page, request }) => {
  const apiUrl = "http://127.0.0.1:18080/api";
  await signInAsAdmin(request, apiUrl);
  const seededAddonsResponse = await request.get(`${apiUrl}/admin/addons`);
  expect(seededAddonsResponse.ok()).toBeTruthy();
  const seededAddons = await seededAddonsResponse.json() as Array<{ available: boolean }>;
  expect(seededAddons.some((addon) => addon.available)).toBeTruthy();

  const addonResponse = await request.post(`${apiUrl}/admin/addons`, {
    data: {
      name: `Local hospitality ${Date.now()}`,
      description: "Enjoy a welcome and hospitality experience with local hosts.",
      preference: "Share dietary or accessibility requirements when booking.",
      price: 25.5,
      available: true,
    },
  });
  expect(addonResponse.status()).toBe(201);
  const createdAddOn = await addonResponse.json() as { id: number; name: string };

  const toursResponse = await request.get(`${apiUrl}/tours`);
  expect(toursResponse.ok()).toBeTruthy();
  const tours = await toursResponse.json() as Array<{
    id: number;
    name: string;
    pricePerPerson: number;
    departures: Array<{ id: number; availableGuests: number }>;
    addons: Array<{
      id: number;
      name: string;
      price: number;
      available: boolean;
    }>;
  }>;
  const tour = tours.find((candidate) =>
    candidate.departures.some((departure) => departure.availableGuests >= 2)
    && candidate.addons.some((addon) => addon.id === createdAddOn.id && addon.available));
  expect(tour, "a departure and a globally available extra is available").toBeTruthy();
  if (!tour) throw new Error("No tour has a departure for two guests and the created extra.");

  const option = tour.addons.find((addon) => addon.id === createdAddOn.id && addon.available);
  if (!option) throw new Error("The new extra was not returned in the tour catalog.");
  const departure = tour.departures.find((candidate) => candidate.availableGuests >= 2);
  if (!departure) throw new Error("The selected tour's departure is no longer available.");
  await page.goto(`/safari-tours/${tour.id}`);
  await expect(page.getByRole("heading", { name: `Request ${tour.name}` })).toBeVisible();
  await page.getByLabel("Departure date").selectOption(String(departure.id));
  await page.getByRole("spinbutton", { name: "Guests" }).fill("2");
  await page.locator(".addon-trigger").click();
  await expect(page.getByText("+$25.50", { exact: true })).toBeVisible();
  await page.getByRole("checkbox", { name: new RegExp(createdAddOn.name) }).check();
  await expect(page.locator(".addon-footer-total")).toContainText("$25.50");
  await page.getByRole("button", { name: "Done" }).click();
  await expect(page.locator(".addon-trigger")).toContainText("1 extra selected");
  await expect(page.locator(".addon-trigger")).toContainText("Additional cost: $25.50");
  const expectedTotal = tour.pricePerPerson * 2 + option.price;
  await expect(page.locator(".booking-price-summary > strong")).toHaveText(`USD ${expectedTotal.toFixed(2)}`);
  await expect(page.locator(".booking-total")).toHaveCount(0);

  const bookingResponsePromise = page.waitForResponse((response) =>
    response.url().endsWith("/api/bookings") && response.request().method() === "POST");
  await page.getByLabel("Full name").fill("Extra Traveller");
  await page.getByLabel("Email").fill(`extra-${Date.now()}@example.com`);
  await page.getByRole("button", { name: "Request booking" }).click();
  const bookingResponse = await bookingResponsePromise;
  expect(bookingResponse.status()).toBe(201);
  const booking = await bookingResponse.json() as {
    totalAmount: number;
    addons: Array<{ addonId: number; name: string; price: number }>;
  };
  expect(booking.totalAmount).toBe(expectedTotal);
  expect(booking.addons).toEqual([
    expect.objectContaining({
      addonId: option.id,
      name: createdAddOn.name,
      price: option.price,
    }),
  ]);

  const deselectResponse = await request.put(`${apiUrl}/admin/addons/${option.id}/availability`, {
    data: { selected: false },
  });
  expect(deselectResponse.ok()).toBeTruthy();
  const deselectedOption = await deselectResponse.json() as { available: boolean };
  expect(deselectedOption.available).toBe(false);
  const reselectResponse = await request.put(`${apiUrl}/admin/addons/${option.id}/availability`, {
    data: { selected: true },
  });
  expect(reselectResponse.ok()).toBeTruthy();
  const reselectedOption = await reselectResponse.json() as { available: boolean };
  expect(reselectedOption.available).toBe(true);
});

test("operator can confirm and decline separate bookings from email links", async ({ page, request }) => {
  const confirmBookingId = await createBooking(
    page,
    `operator-confirm-${Date.now()}@example.com`,
    "Confirm Test Traveller"
  );
  const confirmLinks = await waitForOperatorActionLinks(request, confirmBookingId);

  await openOperatorLink(page, confirmLinks.confirm);
  await expect(page.getByRole("heading", { name: "Booking Request" })).toBeVisible();
  await expect(page.locator("p").filter({ hasText: "Booking status:" })).toContainText("PENDING");
  await page.getByRole("button", { name: "Confirm Availability" }).click();
  await expect(page.locator("main").getByRole("status")).toContainText("updated to CONFIRMED");
  await expect(page.locator("p").filter({ hasText: "Booking status:" })).toContainText("CONFIRMED");

  const declineBookingId = await createBooking(
    page,
    `operator-decline-${Date.now()}@example.com`,
    "Decline Test Traveller"
  );
  const declineLinks = await waitForOperatorActionLinks(request, declineBookingId);

  await openOperatorLink(page, declineLinks.decline);
  await expect(page.getByRole("heading", { name: "Booking Request" })).toBeVisible();
  await expect(page.locator("p").filter({ hasText: "Booking status:" })).toContainText("PENDING");
  await page.getByRole("button", { name: "Decline Request" }).click();
  await expect(page.locator("main").getByRole("status")).toContainText("updated to DECLINED");
  await expect(page.locator("p").filter({ hasText: "Booking status:" })).toContainText("DECLINED");
});

test("customer can complete a simulated payment after the operator confirms the booking", async ({ page, request }) => {
  const email = `payment-${Date.now()}@example.com`;
  const bookingId = await createBooking(page, email, "Payment Test Traveller");
  const actionLinks = await waitForOperatorActionLinks(request, bookingId);

  await openOperatorLink(page, actionLinks.confirm);
  await expect(page.getByRole("button", { name: "Confirm Availability" })).toBeVisible();
  await page.getByRole("button", { name: "Confirm Availability" }).click();
  await expect(page.locator("main").getByRole("status")).toContainText("updated to CONFIRMED");

  await page.goto("/safari-tours");
  await signInWithEmailCode(page, request, email);
  await page.getByRole("link", { name: "My account" }).click();
  const bookingRow = page.getByRole("row").filter({ hasText: "Serengeti Great Migration" });
  await expect(bookingRow).toContainText("CONFIRMED");
  await expect(bookingRow).toContainText("UNPAID");

  let simulatedPaymentComplete = false;
  await page.route("**/api/payments/bookings/*/checkout", async (route) => {
    const reference = `e2e-browser-${bookingId}-${Date.now()}`;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        checkoutUrl: `https://checkout.paystack.com/${reference}`,
        reference,
        provider: "playwright-mock",
        status: "INITIATED",
        paymentAmount: 247065,
        paymentCurrency: "KES",
        exchangeRate: 130.5,
        exchangeRateSource: "test rate",
        quoteExpiresAt: new Date(Date.now() + 3600000).toISOString(),
      }),
    });
  });
  await page.route("**/api/payments/verify", async (route) => {
    simulatedPaymentComplete = true;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        message: "Payment verified",
        bookingId,
        paymentStatus: "PAID",
      }),
    });
  });
  await page.route("**/api/bookings/mine", async (route) => {
    const response = await route.fetch();
    if (!simulatedPaymentComplete) {
      await route.fulfill({ response });
      return;
    }

    const bookings = await response.json() as Array<{ id: number; paymentStatus: string }>;
    await route.fulfill({
      response,
      json: bookings.map((booking) =>
        booking.id === bookingId ? { ...booking, paymentStatus: "PAID" } : booking),
    });
  });
  await page.route("https://checkout.paystack.com/**", async (route) => {
    const reference = new URL(route.request().url()).pathname.split("/").pop();
    if (!reference) throw new Error("Mock checkout URL did not include a payment reference.");
    const callback = new URL("/payment/callback", page.url());
    callback.searchParams.set("reference", reference);
    await route.fulfill({
      contentType: "text/html",
      body: `<!doctype html><html><body>
        <main><h1>Test payment checkout</h1>
        <p>This isolated E2E checkout does not contact a payment provider.</p>
        <button id="complete-payment">Complete test payment</button></main>
        <script>document.getElementById("complete-payment").addEventListener("click", () => {
          window.location.assign(${JSON.stringify(callback.toString())});
        });</script>
      </body></html>`,
    });
  });

  await bookingRow.getByRole("button", { name: "Pay in KES" }).click();
  await expect(bookingRow.locator(".payment-quote")).toContainText("KES");
  await bookingRow.getByRole("button", { name: "Continue to Paystack (KES)" }).click();
  await page.getByRole("button", { name: "Complete test payment" }).click();
  await expect(page).toHaveURL(/\/payment\/callback$/);
  await expect(page.locator("main").getByRole("status")).toContainText("Payment successful!", { timeout: 20_000 });
  await page.goto("/account");
  await expect(bookingRow).toContainText("PAID", { timeout: 20_000 });
});

test("customer can complete a simulated payment for a confirmed vehicle booking", async ({ page, request }) => {
  const apiUrl = "http://127.0.0.1:18080/api";
  const email = `car-payment-${Date.now()}@example.com`;
  await signInAsAdmin(request, apiUrl);

  const carsResponse = await request.get(`${apiUrl}/cars`);
  expect(carsResponse.ok()).toBeTruthy();
  const cars = await carsResponse.json() as Array<{ id: number; name: string }>;
  const car = cars[0];
  if (!car) throw new Error("No available vehicle is seeded for the E2E test.");

  const dayOffset = 10 + Math.floor(Math.random() * 300);
  const startDate = new Date(Date.now() + dayOffset * 86_400_000).toISOString().slice(0, 10);
  const endDate = new Date(Date.now() + (dayOffset + 2) * 86_400_000).toISOString().slice(0, 10);
  const createResponse = await request.post(`${apiUrl}/car-bookings`, {
    data: {
      carId: car.id,
      startDate,
      endDate,
      pickupLocation: "Arusha Airport",
      dropoffLocation: "Arusha Airport",
      passengers: 1,
      guideIncluded: false,
      customerName: "Vehicle Payment Traveller",
      customerEmail: email,
    },
  });
  expect(createResponse.status()).toBe(201);
  const createdBooking = await createResponse.json() as { id: number };
  const confirmResponse = await request.put(`${apiUrl}/admin/car-bookings/${createdBooking.id}/status`, {
    data: { status: "CONFIRMED" },
  });
  expect(confirmResponse.ok()).toBeTruthy();

  await page.goto("/");
  await signInWithEmailCode(page, request, email);
  await page.getByRole("link", { name: "My account" }).click();
  const bookingRow = page.getByRole("row").filter({ hasText: car.name });
  await expect(bookingRow).toContainText("CONFIRMED");
  await expect(bookingRow).toContainText("UNPAID");

  let simulatedPaymentComplete = false;
  await page.route("**/api/payments/car-bookings/*/checkout", async (route) => {
    const reference = `e2e-car-${createdBooking.id}-${Date.now()}`;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        checkoutUrl: `https://checkout.paystack.com/${reference}`,
        reference,
        provider: "playwright-mock",
        status: "INITIATED",
        paymentAmount: 26100,
        paymentCurrency: "KES",
        exchangeRate: 130.5,
        exchangeRateSource: "test rate",
        quoteExpiresAt: new Date(Date.now() + 3600000).toISOString(),
      }),
    });
  });
  await page.route("**/api/payments/verify", async (route) => {
    simulatedPaymentComplete = true;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        message: "Payment verified",
        carBookingId: createdBooking.id,
        paymentStatus: "PAID",
      }),
    });
  });
  await page.route("**/api/car-bookings/mine", async (route) => {
    const response = await route.fetch();
    if (!simulatedPaymentComplete) {
      await route.fulfill({ response });
      return;
    }
    const bookings = await response.json() as Array<{ id: number; paymentStatus: string }>;
    await route.fulfill({
      response,
      json: bookings.map((booking) =>
        booking.id === createdBooking.id ? { ...booking, paymentStatus: "PAID" } : booking),
    });
  });
  await page.route("https://checkout.paystack.com/**", async (route) => {
    const reference = new URL(route.request().url()).pathname.split("/").pop();
    if (!reference) throw new Error("Mock checkout URL did not include a payment reference.");
    const callback = new URL("/payment/callback", page.url());
    callback.searchParams.set("reference", reference);
    await route.fulfill({
      contentType: "text/html",
      body: `<!doctype html><html><body>
        <main><h1>Test payment checkout</h1>
        <p>This isolated E2E checkout does not contact a payment provider.</p>
        <button id="complete-payment">Complete test payment</button></main>
        <script>document.getElementById("complete-payment").addEventListener("click", () => {
          window.location.assign(${JSON.stringify(callback.toString())});
        });</script>
      </body></html>`,
    });
  });

  await bookingRow.getByRole("button", { name: "Pay in KES" }).click();
  await expect(bookingRow.locator(".payment-quote")).toContainText("KES");
  await bookingRow.getByRole("button", { name: "Continue to Paystack (KES)" }).click();
  await page.getByRole("button", { name: "Complete test payment" }).click();
  await expect(page).toHaveURL(/\/payment\/callback$/);
  await expect(page.locator("main").getByRole("status")).toContainText("Payment successful!", { timeout: 20_000 });
  await page.goto("/account");
  await expect(bookingRow).toContainText("PAID", { timeout: 20_000 });
});
