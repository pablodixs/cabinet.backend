package com.scriptles.cabinet.profile.repository;
import com.scriptles.cabinet.profile.entity.Post; import com.scriptles.cabinet.profile.enums.PostStatus; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface PostRepository extends JpaRepository<Post,UUID> { Page<Post> findByAuthorProfileIdAndStatusOrderByPublishedAtDesc(UUID profileId,PostStatus status,Pageable pageable); }
