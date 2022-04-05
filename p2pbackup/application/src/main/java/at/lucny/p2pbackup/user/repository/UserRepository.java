package at.lucny.p2pbackup.user.repository;

import at.lucny.p2pbackup.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    @Query("select DISTINCT u from User u left join fetch u.addresses where u.id in (:ids) order by u.id")
    List<User> fetchAddresses(List<String> ids);

}
