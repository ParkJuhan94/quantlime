package com.quantlime.notice.repository;

import com.quantlime.notice.domain.Notice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Slice<Notice> findAllByOrderByIdDesc(Pageable pageable);
}
