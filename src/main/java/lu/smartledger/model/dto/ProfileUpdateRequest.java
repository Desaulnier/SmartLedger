package lu.smartledger.model.dto;

import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String username;
    private String phone;
    private String avatarUrl;
}
