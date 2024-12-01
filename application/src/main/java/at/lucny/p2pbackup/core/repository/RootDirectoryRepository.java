package at.lucny.p2pbackup.core.repository;

import at.lucny.p2pbackup.core.domain.RootDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RootDirectoryRepository extends JpaRepository<RootDirectory, String> {

    @Query("SELECT rd FROM RootDirectory rd WHERE UPPER(rd.path) LIKE UPPER(:path || '%')")
    List<RootDirectory> findByPathStartsWithIgnoreCase(@Param("path") String path);

    Optional<RootDirectory> findByName(String name);
}
