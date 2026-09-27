import { ActivatedRouteSnapshot, ViewTransitionInfo } from '@angular/router';

function pathOf(root: ActivatedRouteSnapshot): string {
  let leaf = root;
  while (leaf.firstChild) {
    leaf = leaf.firstChild;
  }
  return leaf.pathFromRoot.flatMap((snapshot) => snapshot.url.map((segment) => segment.path)).join('/');
}

export function skipWhenOnlyQueryChanges({ transition, from, to }: ViewTransitionInfo): void {
  if (pathOf(from) === pathOf(to)) {
    transition.skipTransition();
  }
}
