package com.bigcorp.derivatives.core;

/**
 * Derivative order model for the FX/options desk.
 * 
 * This is our own order class -- we don't extend TradeOrder from
 * common-lib because their model doesn't have the fields we need
 * (expiry, strikePrice, premium, contractType, underlying).
 * Talked to the equities team about extending their model but they
 * said "file a JIRA" so we just wrote our own.
 * 
 * XML marshalling is inline here (no XmlHelper dependency).
 * 
 * @author External contractor (FX desk buildout)
 * @since 2004-Q3
 */
public class DerivativeOrder {

    // Contract type constants
    public static final String TYPE_FX_SPOT = "FX_SPOT";
    public static final String TYPE_FX_FORWARD = "FX_FORWARD";
    public static final String TYPE_OPTION_CALL = "OPTION_CALL";
    public static final String TYPE_OPTION_PUT = "OPTION_PUT";

    // Status constants
    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_REJECTED = "REJECTED";

    private String orderId;
    private String clientId;
    private String contractType;
    private String underlying;
    private double strikePrice;
    private int quantity;
    private String expiry;
    private String status;
    private double premium;

    public DerivativeOrder() {
        this.status = STATUS_NEW;
    }

    // --- getters/setters ---

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }

    public String getUnderlying() { return underlying; }
    public void setUnderlying(String underlying) { this.underlying = underlying; }

    public double getStrikePrice() { return strikePrice; }
    public void setStrikePrice(double strikePrice) { this.strikePrice = strikePrice; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getExpiry() { return expiry; }
    public void setExpiry(String expiry) { this.expiry = expiry; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getPremium() { return premium; }
    public void setPremium(double premium) { this.premium = premium; }

    /**
     * Marshal to XML string. We roll our own instead of using XmlHelper
     * because we don't want the common-lib dependency chain and their
     * XML format doesn't match what the FX back-office expects anyway.
     */
    public String toXml() {
        StringBuffer sb = new StringBuffer();
        sb.append("<derivativeOrder>");
        sb.append("<orderId>").append(esc(orderId)).append("</orderId>");
        sb.append("<clientId>").append(esc(clientId)).append("</clientId>");
        sb.append("<contractType>").append(esc(contractType)).append("</contractType>");
        sb.append("<underlying>").append(esc(underlying)).append("</underlying>");
        sb.append("<strikePrice>").append(strikePrice).append("</strikePrice>");
        sb.append("<quantity>").append(quantity).append("</quantity>");
        sb.append("<expiry>").append(esc(expiry)).append("</expiry>");
        sb.append("<status>").append(esc(status)).append("</status>");
        sb.append("<premium>").append(premium).append("</premium>");
        sb.append("</derivativeOrder>");
        return sb.toString();
    }

    /**
     * Unmarshal from XML string. Quick-and-dirty tag extraction --
     * we know the format because we wrote it ourselves.
     */
    public static DerivativeOrder fromXml(String xml) {
        DerivativeOrder order = new DerivativeOrder();
        order.setOrderId(extractTag(xml, "orderId"));
        order.setClientId(extractTag(xml, "clientId"));
        order.setContractType(extractTag(xml, "contractType"));
        order.setUnderlying(extractTag(xml, "underlying"));
        order.setExpiry(extractTag(xml, "expiry"));
        order.setStatus(extractTag(xml, "status"));

        String sp = extractTag(xml, "strikePrice");
        if (sp != null && sp.length() > 0) {
            order.setStrikePrice(Double.parseDouble(sp));
        }
        String qty = extractTag(xml, "quantity");
        if (qty != null && qty.length() > 0) {
            order.setQuantity(Integer.parseInt(qty));
        }
        String prem = extractTag(xml, "premium");
        if (prem != null && prem.length() > 0) {
            order.setPremium(Double.parseDouble(prem));
        }
        return order;
    }

    private static String extractTag(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        int end = xml.indexOf(close);
        if (start < 0 || end < 0) return null;
        return xml.substring(start + open.length(), end);
    }

    private String esc(String val) {
        if (val == null) return "";
        return val;
    }

    public String toString() {
        return "DerivativeOrder[" + orderId + " " + contractType + " " + underlying
                + " strike=" + strikePrice + " qty=" + quantity + " exp=" + expiry
                + " status=" + status + " premium=" + premium + "]";
    }
}
