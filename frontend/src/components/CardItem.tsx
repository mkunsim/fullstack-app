import type { ReactNode, MouseEvent, KeyboardEvent } from "react";

interface CardItemProps {
  children: ReactNode;
  ariaLabel: string;
  onActivate: () => void;
}

export default function CardItem({
  children,
  ariaLabel,
  onActivate,
}: CardItemProps) {
  function handleClick(e: MouseEvent<HTMLElement>) {
    const target = e.target as HTMLElement;

    if (target.closest("button,a,input,select,textarea,label")) {
      return;
    }

    onActivate();
  }

  function handleKeyDown(e: KeyboardEvent<HTMLElement>) {
    if (e.target !== e.currentTarget) return;

    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      onActivate();
    }
  }

  return (
    <article
      className="tour selectable-tour"
      tabIndex={0}
      aria-label={ariaLabel}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
    >
      {children}
    </article>
  );
}