package net.tpky.demoapp;

import java.util.Date;

public class ApplicationGrantDto {
    private String id;
    private String state;
    private Date validBefore;
    private Date validFrom;
    private String timeRestrictionIcal;
    private String issuer;
    private String granteeFirstName;
    private String granteeLastName;
    private String lockTitle;
    private String lockLocation;
    private String physicalLockId;

    public ApplicationGrantDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Date getValidBefore() {
        return validBefore;
    }

    public void setValidBefore(Date validBefore) {
        this.validBefore = validBefore;
    }

    public Date getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Date validFrom) {
        this.validFrom = validFrom;
    }

    public String getTimeRestrictionIcal() {
        return timeRestrictionIcal;
    }

    public void setTimeRestrictionIcal(String timeRestrictionIcal) {
        this.timeRestrictionIcal = timeRestrictionIcal;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getGranteeFirstName() {
        return granteeFirstName;
    }

    public void setGranteeFirstName(String granteeFirstName) {
        this.granteeFirstName = granteeFirstName;
    }

    public String getGranteeLastName() {
        return granteeLastName;
    }

    public void setGranteeLastName(String granteeLastName) {
        this.granteeLastName = granteeLastName;
    }

    public String getLockTitle() {
        return lockTitle;
    }

    public void setLockTitle(String lockTitle) {
        this.lockTitle = lockTitle;
    }

    public String getPhysicalLockId() {
        return physicalLockId;
    }

    public void setPhysicalLockId(String physicalLockId) {
        this.physicalLockId = physicalLockId;
    }

    public String getLockLocation() {
        return lockLocation;
    }

    public void setLockLocation(String lockLocation) {
        this.lockLocation = lockLocation;
    }
}
