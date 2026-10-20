package com.quantlime.notice.implement;

import com.quantlime.notice.domain.Notice;
import com.quantlime.notice.repository.NoticeRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NoticeReader {

    private final NoticeRepository noticeRepository;

    public Slice<Notice> findAllByOrderByIdDesc(Pageable pageable) {
        return noticeRepository.findAllByOrderByIdDesc(pageable);
    }

    public Optional<Notice> findById(Long noticeId) {
        return noticeRepository.findById(noticeId);
    }
}
