package com.quantlime.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.feed.domain.FeedCategory;
import com.quantlime.feed.domain.FeedPost;
import com.quantlime.feed.domain.FeedReport;
import com.quantlime.feed.domain.FeedReportReason;
import com.quantlime.feed.domain.FeedReportTarget;
import com.quantlime.feed.implement.FeedCommentAppender;
import com.quantlime.feed.implement.FeedCommentReader;
import com.quantlime.feed.implement.FeedPostAppender;
import com.quantlime.feed.implement.FeedPostLikeAppender;
import com.quantlime.feed.implement.FeedPostLikeReader;
import com.quantlime.feed.implement.FeedPostReader;
import com.quantlime.feed.implement.FeedReportAppender;
import com.quantlime.feed.implement.FeedReportReader;
import com.quantlime.feed.repository.FeedCommentRepository;
import com.quantlime.feed.repository.FeedPostLikeRepository;
import com.quantlime.feed.repository.FeedPostRepository;
import com.quantlime.feed.repository.FeedReportRepository;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.implement.UserReader;
import com.quantlime.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FeedModerationServiceTest {

    @Mock
    private FeedPostRepository feedPostRepository;

    @Mock
    private FeedCommentRepository feedCommentRepository;

    @Mock
    private FeedPostLikeRepository feedPostLikeRepository;

    @Mock
    private FeedReportRepository feedReportRepository;

    @Mock
    private UserRepository userRepository;

    private FeedModerationService service;

    @BeforeEach
    void setUp() {
        service = new FeedModerationService(
            new FeedPostReader(feedPostRepository), new FeedPostAppender(feedPostRepository),
            new FeedCommentReader(feedCommentRepository), new FeedCommentAppender(feedCommentRepository),
            new FeedPostLikeReader(feedPostLikeRepository), new FeedPostLikeAppender(feedPostLikeRepository),
            new FeedReportReader(feedReportRepository), new FeedReportAppender(feedReportRepository),
            new UserReader(userRepository));
    }

    private User userWithId(long id, String providerId) {
        User user = UserFixture.createUser(OAuthProvider.GOOGLE, providerId);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private FeedPost postBy(User author) {
        return FeedPost.of(author, FeedCategory.DOMESTIC_STOCK, "광고글", null);
    }

    @Test
    @DisplayName("[서로 다른 신고자 3명째 신고에서 글이 자동 숨김된다]")
    void reportPost_thirdReport_hidesPost() {
        // given
        FeedPost post = postBy(userWithId(1L, "author"));
        given(feedPostRepository.findByIdAndHiddenFalse(10L)).willReturn(Optional.of(post));
        given(feedReportRepository.existsByReporter_IdAndTargetTypeAndTargetId(2L, FeedReportTarget.POST, 10L))
            .willReturn(false);
        given(userRepository.findById(2L)).willReturn(Optional.of(userWithId(2L, "r")));
        given(feedReportRepository.countByTargetTypeAndTargetId(FeedReportTarget.POST, 10L)).willReturn(3L);

        // when
        service.reportPost(2L, 10L, FeedReportReason.ADVERTISEMENT);

        // then
        assertThat(post.isHidden()).isTrue();
        verify(feedReportRepository).save(any(FeedReport.class));
    }

    @Test
    @DisplayName("[임계치 미만이면 글은 숨김되지 않는다]")
    void reportPost_belowThreshold_staysVisible() {
        // given
        FeedPost post = postBy(userWithId(1L, "author"));
        given(feedPostRepository.findByIdAndHiddenFalse(10L)).willReturn(Optional.of(post));
        given(userRepository.findById(2L)).willReturn(Optional.of(userWithId(2L, "r")));
        given(feedReportRepository.countByTargetTypeAndTargetId(FeedReportTarget.POST, 10L)).willReturn(2L);

        // when
        service.reportPost(2L, 10L, FeedReportReason.SPAM);

        // then
        assertThat(post.isHidden()).isFalse();
    }

    @Test
    @DisplayName("[같은 사용자의 중복 신고는 저장하지 않고 조용히 무시한다]")
    void reportPost_duplicate_ignored() {
        // given
        FeedPost post = postBy(userWithId(1L, "author"));
        given(feedPostRepository.findByIdAndHiddenFalse(10L)).willReturn(Optional.of(post));
        given(feedReportRepository.existsByReporter_IdAndTargetTypeAndTargetId(2L, FeedReportTarget.POST, 10L))
            .willReturn(true);

        // when
        service.reportPost(2L, 10L, FeedReportReason.SPAM);

        // then
        verify(feedReportRepository, never()).save(any());
        assertThat(post.isHidden()).isFalse();
    }

    @Test
    @DisplayName("[본인 글은 신고할 수 없다]")
    void reportPost_ownPost_throws() {
        // given
        FeedPost post = postBy(userWithId(1L, "author"));
        given(feedPostRepository.findByIdAndHiddenFalse(10L)).willReturn(Optional.of(post));

        // when & then
        assertThatThrownBy(() -> service.reportPost(1L, 10L, FeedReportReason.SPAM))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[이미 숨겨졌거나 없는 글은 404다]")
    void reportPost_hiddenOrMissing_throwsNotFound() {
        // given
        given(feedPostRepository.findByIdAndHiddenFalse(10L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.reportPost(2L, 10L, FeedReportReason.SPAM))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[복구하면 다시 노출하고 신고를 초기화한다]")
    void restorePost_clearsReportsAndUnhides() {
        // given
        FeedPost post = postBy(userWithId(1L, "author"));
        post.hide();
        given(feedPostRepository.findById(10L)).willReturn(Optional.of(post));

        // when
        service.restorePost(10L);

        // then
        assertThat(post.isHidden()).isFalse();
        verify(feedReportRepository).deleteByTargetTypeAndTargetId(FeedReportTarget.POST, 10L);
    }
}
