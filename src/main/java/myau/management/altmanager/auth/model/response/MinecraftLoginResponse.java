package myau.management.altmanager.auth.model.response;

import lombok.Getter;

public class MinecraftLoginResponse {
    @Getter
    private final String username;
    private final String access_token;

    public MinecraftLoginResponse(String username, String access_token) {
        this.username = username;
        this.access_token = access_token;
    }

    public String getAccessToken() {
        return access_token;
    }
}
