package com.CognitiveMemory.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;


@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Use JPA Column unique constraint (not Spring's @Indexed)
    @Column(name = "user_name", unique = true)
    @NonNull
    private String userName;

    private String firstName;
    private String lastName;
    private String mobileNo;
    @NonNull
    private String email;
    @NonNull
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role")
    private List<String> roles;
}
