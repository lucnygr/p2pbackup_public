package at.lucny.p2pbackup.restore.repository;

import at.lucny.p2pbackup.core.domain.BlockMetaDataId;
import at.lucny.p2pbackup.restore.domain.RestoreBlockData;
import at.lucny.p2pbackup.restore.domain.RestoreType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RestoreBlockDataRepository extends JpaRepository<RestoreBlockData, BlockMetaDataId> {

    @Query("SELECT rb.blockMetaDataId.blockMetaData.id FROM RestoreBlockData rb " +
            "INNER JOIN rb.blockMetaDataId bmdId " +
            "INNER JOIN bmdId.blockMetaData bmd " +
            "INNER JOIN bmd.locations l " +
            "WHERE l.userId IN :userIds AND rb.type in (:types)")
    Page<String> findIdsByUserIdsAndTypes(@Param("userIds") List<String> userIds, @Param("types") List<RestoreType> types, Pageable pageRequest);

    long countByTypeIn(List<RestoreType> types);
}
