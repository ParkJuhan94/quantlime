package com.quantlime.score.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Divergence {

    @Column(name = "divergence_flag")
    private Boolean flag;

    @Column(name = "divergence_message", length = 200)
    private String message;

    private Divergence(Boolean flag, String message) {
        this.flag = flag;
        this.message = message;
    }

    public static Divergence of(Boolean flag, String message) {
        return new Divergence(flag, message);
    }
}
