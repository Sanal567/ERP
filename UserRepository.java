package com.isgn.massTransaction.repository;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.isgn.massTransaction.model.Users;

@Repository
public interface UserRepository extends JpaRepository<Users, Long> {
	
	Users findByUsername(String username);
	
	@Modifying
	@Query("update Users u set u.obsolete = :status where u.id = :userId")
	void updateUserStatus(@Param("userId") Integer userId, @Param("status") boolean status);
	
	@Query("select u from Users u where u.username!= :userName and u.deleted = :deleted order by username desc")
	List<Users> findByUsernameAndDeleted(@Param("userName") String userName, @Param("deleted") boolean deleted);
	
	@Modifying
	@Query("update Users u set u.deleted = :deleted where u.id = :userId")
	void deleteUser(@Param("userId") Integer userId, @Param("deleted") boolean deleted);
	
}
