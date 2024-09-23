package host.ivory;

import java.util.Date;

public class Warning {
    private final String userId;
    private final String reason;
    private final Date date;
    private final String issuerId;

    // Constructor
    public Warning(String userId, String reason, Date date, String issuerId) {
        this.userId = userId;
        this.reason = reason;
        this.date = date;
        this.issuerId = issuerId;  // Initialize the issuer's ID
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }

    public Date getDate() {
        return date;
    }

    public String getIssuerId() {
        return issuerId; 
    }
}