package io.josrom.whereismycar.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "categories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_categories_user_label", columnNames = {"user_id", "label"})
})
@Data
@NoArgsConstructor
public class Category {
    /** Default categories store an Android string resource as label: the apps translate it. */
    public static final String SYSTEM_LABEL_PREFIX = "@string/";
    public static final String HOME_LABEL = SYSTEM_LABEL_PREFIX + "category_home";
    public static final String FAVORITES_LABEL = SYSTEM_LABEL_PREFIX + "category_favorites";
    public static final String OTHERS_LABEL = SYSTEM_LABEL_PREFIX + "category_others";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column
    private String label;

    @Column
    private String icon;

    @Column
    private String color;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @JsonIgnore
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private List<SavedLocation> savedLocations;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** A default category: its label is a string resource and cannot be changed. */
    @JsonIgnore
    public boolean isSystem() {
        return label != null && label.startsWith(SYSTEM_LABEL_PREFIX);
    }
}
