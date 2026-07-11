package com.scriptles.cabinet.user.entity;

import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 30, unique = true, nullable = false)
    private String username;

    @Column(length = 254, unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 80, nullable = false)
    private String displayName;

    @Column(length = 500, columnDefinition = "TEXT")
    private String biography;

    @Column(columnDefinition = "TEXT")
    private String avatarUlr;

    @Enumerated(EnumType.STRING)
    private Visibility profileVisibility = Visibility.PUBLIC;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public static User create(String email, String username, String displayName, String passwordHash) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordHash);
        return user;
    }
}
