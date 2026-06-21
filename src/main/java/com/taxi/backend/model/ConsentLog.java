package com.taxi.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Shaxsiy ma'lumotlarga berilgan rozilikning isbotlanadigan yozuvi.
 * UZ "Shaxsga doir ma'lumotlar to'g'risida"gi qonun + Buyruq No.3478 talab qiladigan maydonlar.
 */
@Entity
@Table(name = "consent_log")
public class ConsentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 20)
    private String role;

    // LOGIN | DRIVER_DOCS
    @Column(name = "consent_type", nullable = false, length = 40)
    private String consentType = "LOGIN";

    @Column(name = "policy_version", nullable = false, length = 40)
    private String policyVersion;

    @Column(columnDefinition = "TEXT")
    private String purposes;

    @Column(name = "data_categories", columnDefinition = "TEXT")
    private String dataCategories;

    @Column(name = "third_party_allowed")
    private Boolean thirdPartyAllowed = Boolean.TRUE;

    @Column(name = "cross_border_allowed")
    private Boolean crossBorderAllowed = Boolean.FALSE;

    @Column(name = "public_distribution_allowed")
    private Boolean publicDistributionAllowed = Boolean.FALSE;

    @Column(name = "validity_term", length = 255)
    private String validityTerm;

    @Column(name = "operator_name", length = 255)
    private String operatorName;

    @Column(name = "operator_tin", length = 40)
    private String operatorTin;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ConsentLog() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getConsentType() { return consentType; }
    public void setConsentType(String consentType) { this.consentType = consentType; }

    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }

    public String getPurposes() { return purposes; }
    public void setPurposes(String purposes) { this.purposes = purposes; }

    public String getDataCategories() { return dataCategories; }
    public void setDataCategories(String dataCategories) { this.dataCategories = dataCategories; }

    public Boolean getThirdPartyAllowed() { return thirdPartyAllowed; }
    public void setThirdPartyAllowed(Boolean thirdPartyAllowed) { this.thirdPartyAllowed = thirdPartyAllowed; }

    public Boolean getCrossBorderAllowed() { return crossBorderAllowed; }
    public void setCrossBorderAllowed(Boolean crossBorderAllowed) { this.crossBorderAllowed = crossBorderAllowed; }

    public Boolean getPublicDistributionAllowed() { return publicDistributionAllowed; }
    public void setPublicDistributionAllowed(Boolean publicDistributionAllowed) { this.publicDistributionAllowed = publicDistributionAllowed; }

    public String getValidityTerm() { return validityTerm; }
    public void setValidityTerm(String validityTerm) { this.validityTerm = validityTerm; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getOperatorTin() { return operatorTin; }
    public void setOperatorTin(String operatorTin) { this.operatorTin = operatorTin; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
