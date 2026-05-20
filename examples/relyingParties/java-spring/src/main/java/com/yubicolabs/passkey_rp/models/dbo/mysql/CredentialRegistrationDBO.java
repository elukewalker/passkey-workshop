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
@Table(name = "credential_registrations")
public class CredentialRegistrationDBO {

  @Getter
  @Column(columnDefinition = "text")
  String userHandle;

  @Getter
  @Column(columnDefinition = "text")
  String credentialID;

  @Getter
  @Column(columnDefinition = "text")
  String userIdentity;

  @Getter
  @Setter
  @Column(columnDefinition = "text")
  String credentialNickname;

  @Getter
  long registrationTime;

  @Getter
  long lastUsedTime;

  @Getter
  long lastUpdateTime;

  @Getter
  @Column(columnDefinition = "text")
  String credential;

  /**
   * Denotes an icon given by the FIDO MDS
   */
  @Getter
  @Column(columnDefinition = "text")
  String iconURI;

  @Getter
  @Column(columnDefinition = "boolean default false")
  boolean isHighAssurance;

  @Getter
  @Setter
  @Column(columnDefinition = "text")
  String state;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
}
