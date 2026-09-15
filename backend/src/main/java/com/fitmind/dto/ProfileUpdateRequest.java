package com.fitmind.dto; import lombok.Data; import javax.validation.constraints.*; import java.math.BigDecimal;
@Data public class ProfileUpdateRequest { @NotBlank @Size(max=50) private String nickname; @NotNull @DecimalMin("1.0") private BigDecimal startWeightKg; @NotNull @DecimalMin("1.0") private BigDecimal targetWeightKg; }
