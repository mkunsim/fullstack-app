package com.example.safari.operator;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tour_operators")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Operator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String phone;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 255)
    private String website;

    @Column(length = 255)
    private String motto;

    @Builder
    public Operator(String name, String title, String address, String phone, String email, String website, String motto) {
        this.name = name;
        this.title = title;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.website = website;
        this.motto = motto;
    }
}