package com.quantlime.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.feed.domain.FeedCategory;
import com.quantlime.feed.domain.FeedComment;
import com.quantlime.feed.domain.FeedPost;
import com.quantlime.feed.domain.FeedReport;
import com.quantlime.feed.domain.FeedReportReason;
import com.quantlime.feed.domain.FeedReportTarget;
import com.quantlime.feed.dto.response.AdminFeedCommentResponse;
import com.quantlime.feed.dto.response.AdminFeedPostResponse;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
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

    private FeedComment commentBy(User author, FeedPost post) {
        return FeedComment.of(author, post, "스팸 댓글");
    }

    @Test
    @DisplayName("[댓글도 서로 다른 신고자 3명째에 자동 숨김된다]")
    void reportComment_thirdReport_hidesComment() {
        FeedComment comment = commentBy(userWithId(1L, "author"), postBy(userWithId(9L, "owner")));
        given(feedCommentRepository.findByIdAndHiddenFalse(5L)).willReturn(Optional.of(comment));
        given(userRepository.findById(2L)).willReturn(Optional.of(userWithId(2L, "r")));
        given(feedReportRepository.countByTargetTypeAndTargetId(FeedReportTarget.COMMENT, 5L)).willReturn(3L);

        service.reportComment(2L, 5L, FeedReportReason.SPAM);

        assertThat(comment.isHidden()).isTrue();
        verify(feedReportRepository).save(any(FeedReport.class));
    }

    @Test
    @DisplayName("[댓글 신고 - 임계치 미만이면 숨기지 않고, 중복 신고는 저장하지 않는다]")
    void reportComment_belowThresholdOrDuplicate_staysVisible() {
        FeedComment comment = commentBy(userWithId(1L, "author"), postBy(userWithId(9L, "owner")));
        given(feedCommentRepository.findByIdAndHiddenFalse(5L)).willReturn(Optional.of(comment));
        given(userRepository.findById(2L)).willReturn(Optional.of(userWithId(2L, "r")));
        given(feedReportRepository.countByTargetTypeAndTargetId(FeedReportTarget.COMMENT, 5L)).willReturn(2L);
        service.reportComment(2L, 5L, FeedReportReason.SPAM);
        assertThat(comment.isHidden()).isFalse();

        given(feedReportRepository.existsByReporter_IdAndTargetTypeAndTargetId(3L, FeedReportTarget.COMMENT, 5L))
            .willReturn(true);
        service.reportComment(3L, 5L, FeedReportReason.SPAM);
        verify(feedReportRepository, times(1)).save(any(FeedReport.class));
    }

    @Test
    @DisplayName("[댓글 신고 - 본인 댓글은 거부하고, 없거나 숨겨진 댓글은 404다]")
    void reportComment_ownOrMissing_throws() {
        FeedComment comment = commentBy(userWithId(1L, "author"), postBy(userWithId(9L, "owner")));
        given(feedCommentRepository.findByIdAndHiddenFalse(5L)).willReturn(Optional.of(comment));
        given(feedCommentRepository.findByIdAndHiddenFalse(6L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reportComment(1L, 5L, FeedReportReason.SPAM))
            .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.reportComment(2L, 6L, FeedReportReason.SPAM))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[신고자 계정이 없으면 신고를 저장하지 않고 404다]")
    void reportPost_unknownReporter_throwsNotFound() {
        FeedPost post = postBy(userWithId(1L, "author"));
        given(feedPostRepository.findByIdAndHiddenFalse(10L)).willReturn(Optional.of(post));
        given(userRepository.findById(2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reportPost(2L, 10L, FeedReportReason.SPAM))
            .isInstanceOf(NotFoundException.class);
        verify(feedReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("[숨김 글 목록은 글마다 신고 수를 붙이고, 신고 기록이 없으면 0으로 둔다]")
    void getHiddenPosts_attachesReportCounts() {
        FeedPost reported = postBy(userWithId(1L, "a"));
        ReflectionTestUtils.setField(reported, "id", 10L);
        FeedPost unreported = postBy(userWithId(1L, "a"));
        ReflectionTestUtils.setField(unreported, "id", 11L);
        Pageable pageable = PageRequest.of(0, 20);
        given(feedPostRepository.findHiddenOrderByIdDesc(pageable))
            .willReturn(new SliceImpl<>(List.of(reported, unreported), pageable, false));
        given(feedReportRepository.countByTargetIds(eq(FeedReportTarget.POST), anyCollection()))
            .willReturn(Collections.singletonList(new Object[] {10L, 3L}));

        Slice<AdminFeedPostResponse> result = service.getHiddenPosts(pageable);

        assertThat(result.getContent()).extracting(AdminFeedPostResponse::id, AdminFeedPostResponse::reportCount)
            .containsExactly(tuple(10L, 3L), tuple(11L, 0L));
    }

    @Test
    @DisplayName("[숨김 목록이 비어 있으면 신고 수 조회 쿼리를 보내지 않는다]")
    void getHiddenPosts_empty_skipsCountQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        given(feedPostRepository.findHiddenOrderByIdDesc(pageable))
            .willReturn(new SliceImpl<>(List.of(), pageable, false));

        assertThat(service.getHiddenPosts(pageable).getContent()).isEmpty();
        verify(feedReportRepository, never()).countByTargetIds(any(), anyCollection());
    }

    @Test
    @DisplayName("[숨김 댓글 목록도 신고 수와 원글 id를 담아 돌려준다]")
    void getHiddenComments_attachesReportCounts() {
        FeedPost post = postBy(userWithId(9L, "owner"));
        ReflectionTestUtils.setField(post, "id", 77L);
        FeedComment comment = commentBy(userWithId(1L, "author"), post);
        ReflectionTestUtils.setField(comment, "id", 5L);
        Pageable pageable = PageRequest.of(0, 20);
        given(feedCommentRepository.findHiddenOrderByIdDesc(pageable))
            .willReturn(new SliceImpl<>(List.of(comment), pageable, false));
        given(feedReportRepository.countByTargetIds(eq(FeedReportTarget.COMMENT), anyCollection()))
            .willReturn(Collections.singletonList(new Object[] {5L, 4L}));

        Slice<AdminFeedCommentResponse> result = service.getHiddenComments(pageable);

        assertThat(result.getContent()).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(5L);
            assertThat(r.postId()).isEqualTo(77L);
            assertThat(r.reportCount()).isEqualTo(4L);
        });
    }

    @Test
    @DisplayName("[댓글 복구도 다시 노출하고 신고를 초기화하며, 없는 대상은 404다]")
    void restoreComment_clearsReportsAndUnhides() {
        FeedComment comment = commentBy(userWithId(1L, "author"), postBy(userWithId(9L, "owner")));
        comment.hide();
        given(feedCommentRepository.findById(5L)).willReturn(Optional.of(comment));
        given(feedCommentRepository.findById(6L)).willReturn(Optional.empty());

        service.restoreComment(5L);

        assertThat(comment.isHidden()).isFalse();
        verify(feedReportRepository).deleteByTargetTypeAndTargetId(FeedReportTarget.COMMENT, 5L);
        assertThatThrownBy(() -> service.restoreComment(6L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[글 영구 삭제 - 댓글 신고, 글 신고, 댓글, 좋아요를 먼저 정리한 뒤 글을 지운다]")
    void deletePost_cleansUpDependentsBeforePost() {
        FeedPost post = postBy(userWithId(1L, "author"));
        given(feedPostRepository.findById(10L)).willReturn(Optional.of(post));
        given(feedCommentRepository.findIdsByFeedPostId(10L)).willReturn(List.of(1L, 2L));

        service.deletePost(10L);

        InOrder inOrder = inOrder(feedReportRepository, feedCommentRepository, feedPostLikeRepository, feedPostRepository);
        inOrder.verify(feedReportRepository).deleteByTargetTypeAndTargetIdIn(FeedReportTarget.COMMENT, List.of(1L, 2L));
        inOrder.verify(feedReportRepository).deleteByTargetTypeAndTargetId(FeedReportTarget.POST, 10L);
        inOrder.verify(feedCommentRepository).deleteByFeedPost_Id(10L);
        inOrder.verify(feedPostLikeRepository).deleteByFeedPost_Id(10L);
        inOrder.verify(feedPostRepository).delete(post);
    }

    @Test
    @DisplayName("[댓글 영구 삭제는 신고를 지운 뒤 댓글을 지우고, 없는 대상은 404다]")
    void deleteComment_removesReportsAndComment() {
        FeedComment comment = commentBy(userWithId(1L, "author"), postBy(userWithId(9L, "owner")));
        given(feedCommentRepository.findById(5L)).willReturn(Optional.of(comment));
        given(feedCommentRepository.findById(6L)).willReturn(Optional.empty());
        given(feedPostRepository.findById(11L)).willReturn(Optional.empty());

        service.deleteComment(5L);

        verify(feedReportRepository).deleteByTargetTypeAndTargetId(FeedReportTarget.COMMENT, 5L);
        verify(feedCommentRepository).delete(comment);
        assertThatThrownBy(() -> service.deleteComment(6L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deletePost(11L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.restorePost(11L)).isInstanceOf(NotFoundException.class);
    }
}
