package ba.tfb.tasknest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Poredi po id-u. Hibernate.getClass() umjesto getClass() jer lazy proxy ima
     * generisanu podklasu, a other.getId() umjesto other.id da proxy stigne da se
     * inicijalizuje. Entitet bez id-a jednak je samo samom sebi.
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        BaseEntity other = (BaseEntity) o;
        return id != null && id.equals(other.getId());
    }

    /**
     * Konstantan po tipu, namjerno. Id je null dok se entitet ne perzistira, pa bi
     * hash zasnovan na njemu pukao ako entitet udje u HashSet prije snimanja.
     */
    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}