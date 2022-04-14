package at.lucny.p2pbackup.restore.repository;

import at.lucny.p2pbackup.restore.domain.RestoreBlockData;
import at.lucny.p2pbackup.restore.domain.RestoreType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestoreBlockDataRepository extends JpaRepository<RestoreBlockData, String> {

    @Query("SELECT DISTINCT rb.blockMetaData.id FROM RestoreBlockData rb " +
            "INNER JOIN rb.blockMetaData bmd " +
            "INNER JOIN bmd.locations l " +
            "WHERE l.userId IN :userIds AND rb.type in (:types)")
    Page<String> findIdsByUserIdsAndTypes(@Param("userIds") List<String> userIds, @Param("types") List<RestoreType> types, Pageable pageRequest);

    @Query("SELECT DISTINCT rb.blockMetaData.id FROM RestoreBlockData rb " +
            "INNER JOIN rb.blockMetaData bmd " +
            "LEFT JOIN bmd.locations l " +
            "WHERE l.userId IN :userIds " +
            "OR bmd.locations IS EMPTY")
    Page<String> findIdsByUserIdsOrNoLocations(@Param("userIds") List<String> userIds, Pageable pageRequest);

    Optional<RestoreBlockData> findByBlockMetaDataId(String id);

    long countByTypeIn(List<RestoreType> types);
}
