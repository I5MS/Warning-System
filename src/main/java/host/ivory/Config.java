package host.ivory;

import java.util.List;

public class Config {

    private final String prefix;
    private final String token;
    private final String logChannelId;
    private final String warnRoleId;
    private final List<String> Staff;
    private final List<String> StaffCanRemoveWarn;

    public Config(String prefix, String token, String logChannelId, String warnRoleId, List<String> Staff, List<String> StaffCanRemoveWarn) {
        this.prefix = prefix;
        this.token = token;
        this.logChannelId = logChannelId;
        this.warnRoleId = warnRoleId;
        this.Staff = Staff;
        this.StaffCanRemoveWarn = StaffCanRemoveWarn;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getToken() {
        return token;
    }

    public String getLogChannelId() {
        return logChannelId;
    }

    public String getWarnRoleId() {
        return warnRoleId;
    }

    public List<String> getStaff() {
        return Staff;
    }

    public List<String> getStaffCanRemoveWarn() {
        return StaffCanRemoveWarn;
    }
}
