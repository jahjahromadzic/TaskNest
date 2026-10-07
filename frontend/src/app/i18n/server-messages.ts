import { currentLang } from './lang';

type Rule = [RegExp, string];

const SESSION = 'Sesija nije važeća. Prijavi se ponovo.';

const EXACT: Record<string, string> = {
  'A deactivated account can only be restored by its owner': 'Deaktiviran račun može vratiti samo njegov vlasnik',
  'Administrators cannot be suspended through the API': 'Administratori se ne mogu suspendovati',
  'An account with this email already exists': 'Račun s ovim emailom već postoji',
  'Invalid email or password': 'Pogrešan email ili lozinka',
  'This reset link is invalid or has expired. Ask for a new one.':
    'Ovaj link za promjenu lozinke nije važeći ili je istekao. Zatraži novi.',
  'Invalid refresh token': SESSION,
  'Refresh token has already been used': SESSION,
  'Refresh token has expired': SESSION,
  'Refresh token is missing': SESSION,
  'Notification does not belong to this user': 'Ovo obavještenje ne pripada tebi',
  'Offer does not belong to this user': 'Ova ponuda ne pripada tebi',
  'Offers can only be submitted on published tasks': 'Ponude se mogu slati samo na objavljene oglase',
  'Only a closed task can be reviewed': 'Recenzija je moguća samo za zatvoren oglas',
  'Only an assigned task can be reopened': 'Ponovo se može otvoriti samo dodijeljen oglas',
  'Only pending offers can be accepted': 'Prihvatiti se mogu samo ponude na čekanju',
  'Only pending or accepted offers can be withdrawn': 'Povući se mogu samo ponude na čekanju ili prihvaćene ponude',
  'Task does not belong to this user': 'Ovaj oglas ne pripada tebi',
  'Task is not assigned to this user': 'Ovaj oglas nije dodijeljen tebi',
  'Tasker role is already active on this account': 'Tasker uloga je već aktivna na ovom računu',
  'This conversation is archived and no longer accepts messages': 'Ovaj razgovor je arhiviran i više ne prima poruke',
  'This task has expired and no longer accepts offers': 'Ovaj oglas je istekao i više ne prima ponude',
  "This tasker's account is not active": 'Račun ovog taskera nije aktivan',
  'You are not a participant in this conversation': 'Nisi učesnik ovog razgovora',
  'You cannot submit an offer on your own task': 'Ne možeš poslati ponudu na svoj oglas',
  'You cannot suspend your own account': 'Ne možeš suspendovati svoj račun',
  'You have already reviewed this task': 'Već si ostavio recenziju za ovaj oglas',
  'You have already submitted an offer on this task': 'Već si poslao ponudu na ovaj oglas',
  'This item was changed by someone else. Please reload and try again.':
    'Neko drugi je u međuvremenu promijenio ovo. Osvježi stranicu i pokušaj ponovo.',
  'This account is not active': 'Ovaj račun nije aktivan',
  'You do not have permission to perform this action': 'Nemaš dozvolu za ovu radnju',
  'Authentication is required to access this resource': 'Za ovo se moraš prijaviti',
  'must not be blank': 'ne smije biti prazno',
  'must not be null': 'je obavezno',
  'must be a well-formed email address': 'mora biti ispravna email adresa',
};

const PATTERNS: Rule[] = [
  [/^Too many failed login attempts\. Try again in (\d+) min\.$/, 'Previše neuspjelih pokušaja prijave. Pokušaj ponovo za $1 min.'],
  [/^\w+ not found: .*$/s, 'Traženi podatak nije pronađen'],
  [/^Invalid task transition: .*$/s, 'Ova radnja nije moguća u trenutnom stanju oglasa'],
  [/^Category is not active: .*$/s, 'Ova kategorija nije aktivna'],
  [/^These categories are not active: .*$/s, 'Neke od izabranih kategorija nisu aktivne'],
  [/^Cannot sort by .*$/s, 'Ovo sortiranje nije podržano'],
  [/^Invalid value '.*' for parameter '.*'$/s, 'Neispravan parametar u adresi'],
  [/^size must be between (\d+) and (\d+)$/, 'dužina mora biti između $1 i $2 znakova'],
  [/^must be greater than or equal to (.+)$/, 'mora biti najmanje $1'],
  [/^must be greater than (.+)$/, 'mora biti veće od $1'],
  [/^must be less than or equal to (.+)$/, 'mora biti najviše $1'],
];

const NOTIFICATIONS: Rule[] = [
  [/^New task in your area: (.*)$/s, 'Novi oglas u tvom području: $1'],
  [/^(.*) offered (.*) KM for: (.*)$/s, '$1 nudi $2 KM za: $3'],
  [/^You were hired for: (.*)$/s, 'Angažovan si za: $1'],
  [/^New message about: (.*)$/s, 'Nova poruka o oglasu: $1'],
  [/^Work has started on your task: (.*)$/s, 'Posao je počeo na tvom oglasu: $1'],
  [/^Work has been completed on your task: (.*)$/s, 'Posao je završen na tvom oglasu: $1'],
  [/^The client closed the task: (.*)$/s, 'Klijent je zatvorio oglas: $1'],
  [/^Your task has expired: (.*)$/s, 'Tvoj oglas je istekao: $1'],
  [/^You received a review for: (.*)$/s, 'Dobio si recenziju za: $1'],
  [/^Your task was removed by a moderator: (.*)\. Reason: (.*)$/s, 'Moderator je uklonio tvoj oglas: $1. Razlog: $2'],
  [
    /^The tasker withdrew from your task: (.*)\. It is open again and earlier offers are active\.$/s,
    'Tasker je odustao od tvog oglasa: $1. Oglas je ponovo otvoren i ranije ponude su aktivne.',
  ],
  [/^The client reopened the task: (.*)$/s, 'Klijent je ponovo otvorio oglas: $1'],
  [
    /^Work did not start in time, so your task is open again: (.*)\. Earlier offers are active\.$/s,
    'Posao nije počeo na vrijeme pa je tvoj oglas ponovo otvoren: $1. Ranije ponude su aktivne.',
  ],
  [/^The task was reopened because work did not start in time: (.*)$/s, 'Oglas je ponovo otvoren jer posao nije počeo na vrijeme: $1'],
  [/^Your completed task was closed automatically: (.*)$/s, 'Tvoj završeni oglas je automatski zatvoren: $1'],
  [
    /^The task was closed automatically and counted as completed: (.*)$/s,
    'Oglas je automatski zatvoren i računa se kao završen: $1',
  ],
  [/^Your offer is active again: (.*)$/s, 'Tvoja ponuda je ponovo aktivna: $1'],
];

function applyRules(text: string, rules: Rule[]): string | null {
  for (const [pattern, replacement] of rules) {
    if (pattern.test(text)) {
      return text.replace(pattern, replacement);
    }
  }
  return null;
}

export function translateServerMessage(text: string): string {
  if (currentLang() === 'en') {
    return text;
  }
  return EXACT[text] ?? applyRules(text, PATTERNS) ?? text;
}

export function translateNotification(content: string | undefined): string {
  if (!content || currentLang() === 'en') {
    return content ?? '';
  }
  return applyRules(content, NOTIFICATIONS) ?? content;
}
