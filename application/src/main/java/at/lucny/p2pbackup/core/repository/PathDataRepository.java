package at.lucny.p2pbackup.core.repository;

import at.lucny.p2pbackup.core.domain.PathData;
import at.lucny.p2pbackup.core.domain.RootDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PathDataRepository extends JpaRepository<PathData, String> {

    /**
     * Finds the {@link PathData} with for the given rootDirectory and path, gets te latest {@link at.lucny.p2pbackup.core.domain.PathVersion}
     * of this {@link PathData} and compares the version's hash with the given one. Returns true if the hash is the same, otherwise false.
     * <p>
     * Returns false if no {@link PathData} exists for the given parameters.
     *
     * @param rootDirectory
     * @param path
     * @param hash
     * @return true if the latest version of the pathdata has the same hash, otherwise false
     */
    @Query("SELECT CASE WHEN COUNT(p)> 0 THEN true ELSE false END " +
            "FROM PathData p " +
            "INNER JOIN p.versions v " +
            "WHERE p.rootDirectory = :rootDirectory " +
            "AND p.path = :path AND v.hash = :hash " +
            "AND v.date = ( " +
            "   SELECT MAX(v2.date) " +
            "   FROM PathData p2 INNER JOIN p2.versions v2 where p2.id = p.id " +
            ") ")
    boolean isLatestPathDataVersionHashSame(@Param("rootDirectory") RootDirectory rootDirectory, @Param("path") String path, @Param("hash") String hash);

    @Query("SELECT v.versionBlock.id " +
            "FROM PathData p " +
            "INNER JOIN p.versions v " +
            "WHERE p.rootDirectory = :rootDirectory " +
            "AND v.date = ( " +
            "   SELECT MAX(v2.date) " +
            "   FROM PathData p2 INNER JOIN p2.versions v2 where p2.id = p.id " +
            ") ")
    Set<String> getLatestPathVersionsByRootDirectory(@Param("rootDirectory") RootDirectory rootDirectory);

    /**
     * Finds a {@link PathData} for the given parameters and fetches the {@link at.lucny.p2pbackup.core.domain.PathVersion}s.
     *
     * @param rootDirectory
     * @param path
     * @return
     */
    @Query("SELECT DISTINCT p FROM PathData p " +
            "LEFT JOIN FETCH p.versions " +
            "JOIN FETCH p.rootDirectory " +
            "WHERE p.rootDirectory = :rootDirectory " +
            "AND p.path = :path")
    Optional<PathData> findByRootDirectoryAndPath(@Param("rootDirectory") RootDirectory rootDirectory, @Param("path") String path);

    /**
     * Finds a {@link PathData} for the given parameters and fetches the {@link at.lucny.p2pbackup.core.domain.PathVersion}s.
     *
     * @param rootDirectory
     * @param versionDate
     * @return
     */
    @Query("SELECT DISTINCT p FROM PathData p " +
            "LEFT JOIN FETCH p.versions " +
            "WHERE p.rootDirectory = :rootDirectory " +
            "AND EXISTS ( " +
            "   SELECT v FROM p.versions v " +
            "   WHERE v.date <= :date ) ")
    List<PathData> findByRootDirectoryAndPathVersionForDateExists(@Param("rootDirectory") RootDirectory rootDirectory, @Param("date") LocalDateTime versionDate);

    /**
     * Finds a {@link PathData} for the given parameters and fetches the {@link at.lucny.p2pbackup.core.domain.PathVersion}s.
     *
     * @param id the id of the {@link PathData}
     * @return
     */
    @Query("SELECT DISTINCT p FROM PathData p " +
            "LEFT JOIN FETCH p.versions " +
            "JOIN FETCH p.rootDirectory " +
            "WHERE p.id = :id")
    Optional<PathData> findByIdFetchVersions(@Param("id") String id);

    /**
     * Finds all {@link PathData} for the given parameters that are not deleted.
     *
     * @param rootDirectory
     * @return
     */
    /*
    @Query("SELECT new at.lucny.p2pbackup.core.repository.PathDataIdAndPath(p.id, p.path) " +
            "FROM PathData p " +
            "INNER JOIN p.versions v " +
            "WHERE p.rootDirectory = :rootDirectory " +
            "AND v.deleted = false " +
            "AND v.date = ( " +
            "   SELECT MAX(v2.date) " +
            "   FROM PathData p2 INNER JOIN p2.versions v2 where p2.id = p.id " +
            ") ")
*/
    @Query("SELECT new at.lucny.p2pbackup.core.repository.PathDataIdAndPath(p.id, p.path) " +
            "FROM PathData p " +
            "WHERE p.rootDirectory = :rootDirectory " +
            "AND EXISTS ( " +
            "  SELECT v FROM p.versions v " +
            "  WHERE v.deleted = false " +
            "  AND v.date = ( " +
            "   SELECT MAX(v2.date) " +
            "   FROM PathData p2 INNER JOIN p2.versions v2 where p2.id = p.id " +
            "  ) " +
            ") ")
    List<PathDataIdAndPath> findAllByRootDirectoryAndNotDeleted(RootDirectory rootDirectory);
}
