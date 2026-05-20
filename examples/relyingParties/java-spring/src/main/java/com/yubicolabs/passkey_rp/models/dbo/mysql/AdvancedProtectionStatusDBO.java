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
@Table(name = "advanced_protection_status")
public class AdvancedProtectionStatusDBO {

  @Getter
  @Column(columnDefinition = "text")
  String userHandle;

  @Getter
  @Setter
  Boolean isAdvancedProtection;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

}
