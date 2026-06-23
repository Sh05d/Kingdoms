package com.kingdom.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Player {
    @Id
    private Integer id;

    @Column(nullable = false)
    private String displayName;

    @Column(length = 1000)
    private String interests;

    @Column(columnDefinition = "datetime")
    private LocalDateTime joinedAt;

    @Column(columnDefinition = "TEXT")
    private String wakatimeApiKey;

    @OneToOne
    @MapsId
    @JoinColumn(name= "id", nullable = false)
    @JsonIgnore
    private User user;

    @OneToOne(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    @PrimaryKeyJoinColumn
    private Subscription subscription;

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL)
    private Set<ConnectedAccount> connectedAccounts;
}
