package ba.tfb.tasknest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tasker_profiles")
@Getter
@Setter
@NoArgsConstructor
public class TaskerProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "headline", length = 150)
    private String headline;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "completed_jobs_count")
    private Integer completedJobsCount = 0;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "tasker_categories",
            joinColumns = @JoinColumn(name = "tasker_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "tasker_municipalities",
            joinColumns = @JoinColumn(name = "tasker_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "municipality_id")
    )
    private Set<Municipality> municipalities = new HashSet<>();
}
