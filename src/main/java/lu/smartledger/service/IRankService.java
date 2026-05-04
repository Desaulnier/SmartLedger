package lu.smartledger.service;

import lu.smartledger.model.dto.RankItemDTO;

import java.util.List;
import java.util.Map;

public interface IRankService {
    List<RankItemDTO> getLeaderboard(Long currentUserId, String period);

    RankItemDTO getMyRank(Long currentUserId, String period);

    List<Map<String, Object>> getAchievements(Long currentUserId);
}