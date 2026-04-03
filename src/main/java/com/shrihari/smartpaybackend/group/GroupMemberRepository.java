package com.shrihari.smartpaybackend.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupId(Long groupId);

    Optional<GroupMember> findByGroup_IdAndUser_Id(Long groupId, Long userId);

    @Query("""
    SELECT gm
    FROM GroupMember gm
    JOIN FETCH gm.group g
    JOIN FETCH g.createdBy
    WHERE gm.user.id = :userId
""")
    List<GroupMember> findAllWithGroupAndCreator(Long userId);

    boolean existsByGroup_IdAndUser_Id(Long groupId, Long userId);


}
