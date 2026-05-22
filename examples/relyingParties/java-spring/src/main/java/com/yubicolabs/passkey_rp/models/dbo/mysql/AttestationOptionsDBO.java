package com.yubicolabs.passkey_rp.models.dbo.mysql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "attestation_requests")
public class AttestationOptionsDBO {
  @Getter
  @Column(columnDefinition = "text")
  String attestationRequest;

  @Getter
  @Setter
  Boolean isActive;

  @Getter
  @Column(columnDefinition = "text")
  String requestId;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
}
