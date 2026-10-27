package io.josrom.whereismycar.entity;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "saved_locations")
@Data 
@NoArgsConstructor 
@AllArgsConstructor 
public class SavedLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "display_name")
    private String displayName;

    @Column
    private String country;

    @Column
    private String city;

    @Column
    private String address;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    /** Stored as JSON (a serialized Java map would not fit in a VARBINARY and could not be read in SQL). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "address_data", columnDefinition = "json")
    private HashMap<String, String> addressData;

    @Column
    private Date date;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}