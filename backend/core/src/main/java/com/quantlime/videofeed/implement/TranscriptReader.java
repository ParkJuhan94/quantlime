package com.quantlime.videofeed.implement;

import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.repository.TranscriptRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link TranscriptRepository}의 자막 조회을 감싸는 구현 레이어(Implementation) - 서비스가 Data Access(Repository)를
 * 직접 건드리지 않고 이 컴포넌트를 통해서만 접근하게 한다. 메서드 이름은 Repository와 같게 두어
 * 호출부 변환이 위임 한 겹으로 끝나게 했다.
 */
@Component
@RequiredArgsConstructor
public class TranscriptReader {

    private final TranscriptRepository transcriptRepository;

    public Optional<Transcript> findByVideo(Video video) {
        return transcriptRepository.findByVideo(video);
    }
}
