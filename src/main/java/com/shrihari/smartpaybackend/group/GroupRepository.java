package com.shrihari.smartpaybackend.group;


import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, Long> {

    boolean existsByGroupCode(String groupCode);
    java.util.Optional<Group> findByGroupCode(String groupCode);

}
