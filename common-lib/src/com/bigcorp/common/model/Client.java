package com.bigcorp.common.model;

import java.io.Serializable;

/**
 * Client / customer record.
 * 
 * Tier was originally just GOLD and SILVER. BRONZE was added in 2001 
 * when sales wanted "everyone to feel special." PLATINUM was added 
 * in 2002 for the Henderson account specifically.
 * 
 * @author Bob
 * @since 1.0
 */
public class Client implements Serializable {

    private static final long serialVersionUID = 101L;

    public static final String TIER_PLATINUM = "PLATINUM";
    public static final String TIER_GOLD = "GOLD";
    public static final String TIER_SILVER = "SILVER";
    public static final String TIER_BRONZE = "BRONZE";

    private String clientId;
    private String name;
    private String email;
    private String phone;
    private String tier;
    private double maxOrderValue; // per-client order limit
    private boolean active;

    public Client() {
        this.tier = TIER_BRONZE;
        this.active = true;
        this.maxOrderValue = 100000.0; // default, can be overridden
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public double getMaxOrderValue() {
        return maxOrderValue;
    }

    public void setMaxOrderValue(double maxOrderValue) {
        this.maxOrderValue = maxOrderValue;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String toString() {
        return "Client[" + clientId + " " + name + " " + tier + "]";
    }
}
