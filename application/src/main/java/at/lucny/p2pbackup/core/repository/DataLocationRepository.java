package at.lucny.p2pbackup.core.repository;

import at.lucny.p2pbackup.core.domain.DataLocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DataLocationRepository extends JpaRepository<DataLocation, String> {

    @Query("SELECT location FROM DataLocation location " +
            "WHERE location.blockMetaData.id = :bmdId AND location.userId = :userId")
    Optional<DataLocation> findByBlockMetaDataIdAndUserId(@Param("bmdId") String bmdId, @Param("userId") String userId);

    @Query("SELECT location.id FROM DataLocation location " +
            "WHERE location.verified < :verifyOlderThan " +
            "AND location.userId in (:userIds)")
    Page<String> findDataLocationIdsToVerify(@Param("verifyOlderThan") LocalDateTime verifyOlderThan, @Param("userIds") List<String> userIds, Pageable pageRequest);

    Page<DataLocation> findByIdIn(List<String> ids, Pageable pageRequest);

    @Query("SELECT location FROM DataLocation location " +
            "WHERE location.blockMetaData.id = :bmdId")
    List<DataLocation> findByBlockMetaDataId(@Param("bmdId") String bmdId);

    @Modifying
    @Query("UPDATE DataLocation location " +
            "SET location.verified = :verifiedDate " +
            "WHERE location.userId = :userId")
    int updateVerifiedDateByUserId(@Param("userId") String userId, LocalDateTime verifiedDate);
}
