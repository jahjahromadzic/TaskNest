package ba.tfb.tasknest.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stabilan oblik paginiranog odgovora.
 * <p>
 * Spring Data upozorava da serijalizovani oblik njegovog Page-a nije dio javnog
 * ugovora i da se moze mijenjati izmedju verzija. Direktnim vracanjem Page-a u
 * odgovor su curili i interni detalji (pageable.offset, sort.unsorted, unpaged),
 * a sort se pojavljivao dvaput. Ovdje je samo ono sto klijentu treba da iscrta
 * listu i paginaciju.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
