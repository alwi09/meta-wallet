package com.enigma.metawallet.entity;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "m_wallet")
@Builder(toBuilder = true)
public class Wallet {

    @Id
    @GenericGenerator(strategy = "uuid2", name = "system-uuid")
    @GeneratedValue(generator = "system-uuid")
    private String id;

    @Column(nullable = false)
    private Long balance;

    @OneToOne
    @JoinColumn(name = "user_credential_id")
    private UserCredential userCredential;
}