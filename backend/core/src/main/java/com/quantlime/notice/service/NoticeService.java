package com.quantlime.notice.service;

import com.quantlime.common.dto.PageResponse;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.notice.domain.Notice;
import com.quantlime.notice.dto.mapper.NoticeMapper;
import com.quantlime.notice.dto.request.CreateNoticeRequest;
import com.quantlime.notice.dto.request.UpdateNoticeRequest;
import com.quantlime.notice.dto.response.CreateNoticeResponse;
import com.quantlime.notice.dto.response.NoticeResponse;
import com.quantlime.notice.dto.response.NoticeSummaryResponse;
import com.quantlime.notice.exception.NoticeErrorCode;
import com.quantlime.notice.implement.NoticeAppender;
import com.quantlime.notice.implement.NoticeReader;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.service.FcmPushService;
import com.quantlime.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private static final int NOTIFICATION_CONTENT_LENGTH = 100;

    private final NoticeReader noticeReader;
    private final NoticeAppender noticeAppender;
    private final FcmPushService fcmPushService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public PageResponse<NoticeSummaryResponse> getNotices(Pageable pageable) {
        return PageResponse.of(noticeReader.findAllByOrderByIdDesc(pageable).map(NoticeMapper::toNoticeSummaryResponse));
    }

    @Transactional(readOnly = true)
    public NoticeResponse getNotice(Long noticeId) {
        return NoticeMapper.toNoticeResponse(getById(noticeId));
    }

    // 알림 발송은 공지 저장과 트랜잭션을 분리한다 - 알림이 실패해도 이미 등록된 공지가 롤백되면 관리자가 다시 등록하다
    // 같은 공지가 중복될 수 있어, 공지는 저장하고 알림 결과만 응답으로 알려준다.
    public CreateNoticeResponse create(CreateNoticeRequest request) {
        Notice saved = noticeAppender.save(Notice.of(request.title().trim(), request.content().trim()));
        boolean notificationSent = request.sendNotification() && notifyAllUsers(saved);
        log.info("공지 등록 완료: noticeId={}, 알림발송요청={}, 알림성공={}",
            saved.getId(), request.sendNotification(), notificationSent);
        return new CreateNoticeResponse(saved.getId(), request.sendNotification(), notificationSent);
    }

    @Transactional
    public NoticeResponse update(Long noticeId, UpdateNoticeRequest request) {
        Notice notice = getById(noticeId);
        notice.update(request.title().trim(), request.content().trim());
        return NoticeMapper.toNoticeResponse(notice);
    }

    @Transactional
    public void delete(Long noticeId) {
        noticeAppender.delete(getById(noticeId));
        log.info("공지 삭제 완료: noticeId={}", noticeId);
    }

    private boolean notifyAllUsers(Notice notice) {
        try {
            fcmPushService.sendToUsers(userService.getAllUserIds(), NotificationType.ADMIN_NOTICE, notice.getTitle(),
                NoticeMapper.summarize(notice.getContent(), NOTIFICATION_CONTENT_LENGTH),
                "/notices/" + notice.getId());
            return true;
        } catch (RuntimeException e) {
            log.error("공지 알림 발송 실패(공지는 등록됨): noticeId={}, error={}", notice.getId(), e.getMessage(), e);
            return false;
        }
    }

    private Notice getById(Long noticeId) {
        return noticeReader.findById(noticeId)
            .orElseThrow(() -> new NotFoundException(NoticeErrorCode.NOT_FOUND_NOTICE));
    }
}
