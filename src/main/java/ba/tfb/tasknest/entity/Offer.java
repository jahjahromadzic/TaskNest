package ba.tfb.tasknest.entity;

import ba.tfb.tasknest.entity.enums.OfferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "offers")
@Getter
@Setter
@NoArgsConstructor
public class Offer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tasker_id", nullable = false)
    private User tasker;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "message", length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OfferStatus status = OfferStatus.PENDING;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;
}
