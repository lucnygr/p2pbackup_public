package at.lucny.p2pbackup.core.repository;

import at.lucny.p2pbackup.core.domain.BlockMetaData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BlockMetaDataRepository extends JpaRepository<BlockMetaData, String> {

    @Query("SELECT distinct bmd FROM BlockMetaData bmd LEFT JOIN FETCH bmd.locations " +
            "WHERE bmd.hash = :hash")
    Optional<BlockMetaData> findByHashFetchLocations(@Param("hash") String hash);

    @Query("SELECT distinct bmd FROM BlockMetaData bmd LEFT JOIN FETCH bmd.locations " +
            "WHERE bmd.id = :id")
    Optional<BlockMetaData> findByIdFetchLocations(@Param("id") String id);

    @Query("SELECT distinct bmd FROM BlockMetaData bmd LEFT JOIN FETCH bmd.locations " +
            "WHERE bmd.id IN (:ids)")
    List<BlockMetaData> findByIdsFetchLocations(@Param("ids") List<String> ids);

    @Query("SELECT distinct bmd FROM BlockMetaData bmd LEFT JOIN FETCH bmd.locations")
    List<BlockMetaData> findAllFetchLocations();

    @Query("SELECT distinct bmd FROM BlockMetaData bmd " +
            "LEFT JOIN FETCH bmd.locations " +
            "WHERE ( " +
            "  SELECT count(location) FROM bmd.locations location " +
            "  WHERE location.verified > :verificationInvalidDate " +
            ") < :nrOfReplicas AND " +
            "NOT EXISTS ( " +
            "  SElECT cu FROM CloudUpload cu " +
            "  WHERE cu.blockMetaData = bmd " +
            ") ")
    List<BlockMetaData> findBlocksWithNotEnoughVerifiedReplicas(@Param("nrOfReplicas") long nrOfReplicas, @Param("verificationInvalidDate") LocalDateTime verificationInvalidDate);

    @Query("SELECT CASE WHEN (COUNT(bmd) > 0) THEN TRUE ELSE FALSE END " +
            "FROM BlockMetaData bmd " +
            "WHERE ( " +
            "  SELECT count(location) FROM bmd.locations location " +
            "  WHERE location.verified > :verificationInvalidDate " +
            ") < :nrOfReplicas AND " +
            "NOT EXISTS ( " +
            "  SElECT cu FROM CloudUpload cu " +
            "  WHERE cu.blockMetaData = bmd " +
            ") AND bmd.id = :bmdId ")
    boolean hasNotEnoughVerifiedReplicas(@Param("bmdId") String bmdId, @Param("nrOfReplicas") long nrOfReplicas, @Param("verificationInvalidDate") LocalDateTime verificationInvalidDate);

    List<BlockMetaData> findAllByIdLike(String bmdId, Pageable pageRequest);

    @Query("SELECT COUNT(bmd) FROM BlockMetaData bmd " +
            "WHERE (SELECT COUNT(location) FROM DataLocation location " +
            "       WHERE location.blockMetaData = bmd AND location.verified >= :verificationInvalidDate " +
            "      ) = :nrOfReplicas ")
    long countNumberOfVerifiedReplicas(@Param("nrOfReplicas") long nrOfReplicas, @Param("verificationInvalidDate") LocalDateTime verificationInvalidDate);

    @Query("SELECT COUNT(bmd) FROM BlockMetaData bmd " +
            "WHERE (SELECT COUNT(location) FROM DataLocation location " +
            "       WHERE location.blockMetaData = bmd " +
            "      ) = :nrOfReplicas ")
    long countNumberOfReplicas(@Param("nrOfReplicas") long nrOfReplicas);
}
