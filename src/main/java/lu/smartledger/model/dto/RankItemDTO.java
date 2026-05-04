package lu.smartledger.model.dto;

import lombok.Data;

@Data
public class RankItemDTO {
    private Long id;
    private String name;
    private String avatar;
    private String grade;
    private String gradeType;
    private Integer score;
    private String desc;
    private Boolean isMe;
    private String avatarUrl;
}
