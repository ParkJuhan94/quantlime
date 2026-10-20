package com.quantlime.notice.implement;

import com.quantlime.notice.domain.Notice;
import com.quantlime.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NoticeAppender {

    private final NoticeRepository noticeRepository;

    public Notice save(Notice notice) {
        return noticeRepository.save(notice);
    }

    public void delete(Notice notice) {
        noticeRepository.delete(notice);
    }
}
