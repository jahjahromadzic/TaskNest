package ba.tfb.tasknest.messaging;

import java.util.UUID;

/**
 * Oglas je objavljen i spreman je za ponude.
 * <p>
 * Nosi samo identifikatore i naslov - sve ostalo potrosac moze dohvatiti iz
 * baze. Poruka koja nosi cijeli entitet zastarijeva cim se entitet promijeni.
 */
public record TaskPublishedEvent(
        UUID taskId,
        String title,
        UUID categoryId,
        UUID municipalityId,

        /** Vlasnik oglasa; iskljucuje se iz notifikacija jer nalog moze imati obje role. */
        UUID clientId
) {
}
