package at.lucny.p2pbackup.core.repository;

import at.lucny.p2pbackup.core.domain.PathVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PathVersionRepository extends JpaRepository<PathVersion, String> {

    @Query("SELECT DISTINCT pv FROM PathVersion pv " +
            "LEFT JOIN FETCH pv.blocks " +
            "WHERE pv.id = :id ")
    PathVersion findByIdFetchBlockMetaData(@Param("id") String id);
}
