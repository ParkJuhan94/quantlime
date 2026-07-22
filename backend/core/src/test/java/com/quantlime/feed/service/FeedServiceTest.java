package com.quantlime.feed.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.feed.domain.FeedCategory;
import com.quantlime.feed.domain.FeedPost;
import com.quantlime.feed.dto.request.CreateFeedCommentRequest;
import com.quantlime.feed.dto.request.CreateFeedPostRequest;
import com.quantlime.feed.dto.request.UpdateFeedPostRequest;
import com.quantlime.feed.dto.response.FeedCommentResponse;
import com.quantlime.feed.dto.response.FeedPostResponse;
import com.quantlime.feed.repository.FeedCommentRepository;
import com.quantlime.feed.repository.FeedPostLikeRepository;
import com.quantlime.feed.repository.FeedPostRepository;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private FeedPostRepository feedPostRepository;

    @Mock
    private FeedPostLikeRepository feedPostLikeRepository;

    @Mock
    private FeedCommentRepository feedCommentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FeedService feedService;

    @Test
    @DisplayName("[존재하는 사용자·주제로 글을 작성하면 저장하고 응답으로 변환한다]")
    void createPost_validRequest_savesAndReturnsResponse() {
        // given
        User user = UserFixture.createUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        FeedPost saved = FeedPost.of(user, FeedCategory.DOMESTIC_STOCK, "오늘 장 어때요");
        given(feedPostRepository.save(any(FeedPost.class))).willReturn(saved);

        // when
        FeedPostResponse response = feedService.createPost(
            1L, new CreateFeedPostRequest("국내주식토론", "오늘 장 어때요", null));

        // then
        assertThat(response.title()).isEqualTo("오늘 장 어때요");
        assertThat(response.category()).isEqualTo("국내주식토론");
        assertThat(response.nickname()).isEqualTo(user.getNickname());
    }

    @Test
    @DisplayName("[존재하지 않는 사용자면 NotFoundException을 던진다]")
    void createPost_userNotFound_throws() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> feedService.createPost(
            999L, new CreateFeedPostRequest("국내주식토론", "제목", null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[존재하지 않는 주제면 ValidationException을 던진다]")
    void createPost_invalidCategory_throws() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(UserFixture.createUser()));

        // when & then
        assertThatThrownBy(() -> feedService.createPost(
            1L, new CreateFeedPostRequest("없는주제", "제목", null)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[category가 없으면 전체 글을, 있으면 해당 주제 글만 조회한다]")
    void getPosts_withAndWithoutCategory_usesCorrectQuery() {
        // given
        User user = UserFixture.createUser();
        FeedPost post = FeedPost.of(user, FeedCategory.CHAT, "아무말");
        ReflectionTestUtils.setField(post, "id", 1L);
        given(feedPostRepository.findAllOrderByIdDesc(any())).willReturn(
            new SliceImpl<>(List.of(post)));
        given(feedPostRepository.findByCategoryOrderByIdDesc(eq(FeedCategory.CHAT), any())).willReturn(
            new SliceImpl<>(List.of(post)));
        given(feedPostLikeRepository.countByPostIds(any())).willReturn(List.of());
        given(feedCommentRepository.countByPostIds(any())).willReturn(List.of());

        // when
        var all = feedService.getPosts(null, null, PageRequest.of(0, 10));
        var chatOnly = feedService.getPosts("아무말대잔치", null, PageRequest.of(0, 10));

        // then
        assertThat(all.getContent()).hasSize(1);
        assertThat(chatOnly.getContent()).hasSize(1);
        verify(feedPostRepository).findAllOrderByIdDesc(any());
        verify(feedPostRepository).findByCategoryOrderByIdDesc(FeedCategory.CHAT, PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("[좋아요 수·댓글 수·내가 좋아요했는지를 함께 채워 반환한다]")
    void getPosts_fillsLikeCountCommentCountAndLikedByMe() {
        // given
        User user = UserFixture.createUser();
        FeedPost post = FeedPost.of(user, FeedCategory.CHAT, "아무말");
        ReflectionTestUtils.setField(post, "id", 1L);
        given(feedPostRepository.findAllOrderByIdDesc(any())).willReturn(new SliceImpl<>(List.of(post)));
        given(feedPostLikeRepository.countByPostIds(any())).willReturn(
            Collections.singletonList(new Object[]{1L, 3L}));
        given(feedCommentRepository.countByPostIds(any())).willReturn(
            Collections.singletonList(new Object[]{1L, 2L}));
        given(feedPostLikeRepository.findLikedPostIds(eq(1L), any())).willReturn(List.of());

        // when
        var result = feedService.getPosts(null, 1L, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent().get(0).likeCount()).isEqualTo(3L);
        assertThat(result.getContent().get(0).commentCount()).isEqualTo(2L);
        assertThat(result.getContent().get(0).likedByMe()).isFalse();
    }

    @Test
    @DisplayName("[글 작성자 본인이 조회하면 mine=true, 다른 사용자가 조회하면 mine=false다]")
    void getPosts_mineReflectsWhetherViewerIsAuthor() {
        // given
        User author = UserFixture.createUser();
        ReflectionTestUtils.setField(author, "id", 1L);
        FeedPost post = FeedPost.of(author, FeedCategory.CHAT, "아무말");
        ReflectionTestUtils.setField(post, "id", 10L);
        given(feedPostRepository.findAllOrderByIdDesc(any())).willReturn(new SliceImpl<>(List.of(post)));
        given(feedPostLikeRepository.countByPostIds(any())).willReturn(List.of());
        given(feedCommentRepository.countByPostIds(any())).willReturn(List.of());
        given(feedPostLikeRepository.findLikedPostIds(any(), any())).willReturn(List.of());

        // when
        var asAuthor = feedService.getPosts(null, 1L, PageRequest.of(0, 10));
        var asOtherUser = feedService.getPosts(null, 2L, PageRequest.of(0, 10));
        var asGuest = feedService.getPosts(null, null, PageRequest.of(0, 10));

        // then
        assertThat(asAuthor.getContent().get(0).mine()).isTrue();
        assertThat(asOtherUser.getContent().get(0).mine()).isFalse();
        assertThat(asGuest.getContent().get(0).mine()).isFalse();
    }

    @Test
    @DisplayName("[좋아요를 누르면 저장하고, 이미 좋아요한 상태면 조용히 무시한다]")
    void likePost_notYetLiked_savesLike_alreadyLiked_noop() {
        // given
        User user = UserFixture.createUser();
        FeedPost post = FeedPost.of(user, FeedCategory.CHAT, "아무말");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(feedPostRepository.findById(1L)).willReturn(Optional.of(post));
        given(feedPostLikeRepository.existsByUser_IdAndFeedPost_Id(1L, 1L)).willReturn(false, true);

        // when
        feedService.likePost(1L, 1L);
        feedService.likePost(1L, 1L);

        // then
        verify(feedPostLikeRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("[좋아요 취소는 삭제 쿼리를 그대로 위임한다]")
    void unlikePost_delegatesDelete() {
        // when
        feedService.unlikePost(1L, 1L);

        // then
        verify(feedPostLikeRepository).deleteByUser_IdAndFeedPost_Id(1L, 1L);
    }

    @Test
    @DisplayName("[댓글을 작성하면 저장하고 응답으로 변환한다]")
    void createComment_validRequest_savesAndReturnsResponse() {
        // given
        User user = UserFixture.createUser();
        FeedPost post = FeedPost.of(user, FeedCategory.CHAT, "아무말");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(feedPostRepository.findById(1L)).willReturn(Optional.of(post));
        given(feedCommentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when
        FeedCommentResponse response = feedService.createComment(1L, 1L, new CreateFeedCommentRequest("좋은 글이네요"));

        // then
        assertThat(response.content()).isEqualTo("좋은 글이네요");
        assertThat(response.nickname()).isEqualTo(user.getNickname());
    }

    @Test
    @DisplayName("[존재하지 않는 글에 댓글을 달면 NotFoundException을 던진다]")
    void createComment_postNotFound_throws() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(UserFixture.createUser()));
        given(feedPostRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> feedService.createComment(1L, 999L, new CreateFeedCommentRequest("내용")))
            .isInstanceOf(NotFoundException.class);
        verify(feedCommentRepository, never()).save(any());
    }

    @Test
    @DisplayName("[작성자 본인이 수정하면 내용을 바꾸고 mine=true로 응답한다]")
    void updatePost_owner_updatesAndReturnsResponse() {
        // given
        User user = UserFixture.createUser();
        FeedPost post = FeedPost.of(user, FeedCategory.CHAT, "원래 제목");
        given(feedPostRepository.findByIdAndUser_Id(1L, 1L)).willReturn(Optional.of(post));
        given(feedPostLikeRepository.countByPostIds(any())).willReturn(List.of());
        given(feedCommentRepository.countByPostIds(any())).willReturn(List.of());
        given(feedPostLikeRepository.existsByUser_IdAndFeedPost_Id(1L, 1L)).willReturn(false);

        // when
        FeedPostResponse response = feedService.updatePost(
            1L, 1L, new UpdateFeedPostRequest("국내주식토론", "바뀐 제목", null));

        // then
        assertThat(response.title()).isEqualTo("바뀐 제목");
        assertThat(response.category()).isEqualTo("국내주식토론");
        assertThat(response.mine()).isTrue();
    }

    @Test
    @DisplayName("[다른 사용자의 글을 수정하려 하면 NotFoundException을 던진다]")
    void updatePost_notOwner_throws() {
        // given
        given(feedPostRepository.findByIdAndUser_Id(1L, 2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> feedService.updatePost(
            2L, 1L, new UpdateFeedPostRequest("국내주식토론", "제목", null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[작성자 본인이 삭제하면 댓글·좋아요를 먼저 지우고 글을 삭제한다]")
    void deletePost_owner_deletesCommentsLikesThenPost() {
        // given
        User user = UserFixture.createUser();
        FeedPost post = FeedPost.of(user, FeedCategory.CHAT, "삭제될 글");
        given(feedPostRepository.findByIdAndUser_Id(1L, 1L)).willReturn(Optional.of(post));

        // when
        feedService.deletePost(1L, 1L);

        // then
        verify(feedCommentRepository).deleteByFeedPost_Id(1L);
        verify(feedPostLikeRepository).deleteByFeedPost_Id(1L);
        verify(feedPostRepository).delete(post);
    }

    @Test
    @DisplayName("[다른 사용자의 글을 삭제하려 하면 NotFoundException을 던지고 아무것도 지우지 않는다]")
    void deletePost_notOwner_throwsAndDeletesNothing() {
        // given
        given(feedPostRepository.findByIdAndUser_Id(1L, 2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> feedService.deletePost(2L, 1L))
            .isInstanceOf(NotFoundException.class);
        verify(feedPostRepository, never()).delete(any());
        verify(feedCommentRepository, never()).deleteByFeedPost_Id(any());
    }
}
