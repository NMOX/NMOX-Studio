package org.nmox.studio.editor.outline;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outline sees your Angular routes (v1.313.0).
 *
 * <p>{@code app.routes.ts} is the file an Angular developer opens to see
 * how the application is put together, and it outlined as NOTHING —
 * measured on a real routes file before a line of this was written. The
 * extractor was right: the file declares one exported array and no
 * functions or classes. It was also useless, because the structure of
 * that file IS its route table. Same finding as v1.292.0's Express
 * server files, this time in the framework this project has bet on.
 *
 * <p>The rule is narrow on purpose — it fires only in a file that both
 * imports {@code @angular/router} and annotates a {@code Routes} type —
 * because a wrong outline entry is worse than a missing one. These tests
 * pin both halves: the routes it must find, and the files it must leave
 * alone.
 */
class OutlineNgRouteTest {

    private static List<OutlineModel.Item> ts(String src) {
        return OutlineModel.extract("text/typescript", src);
    }

    private static List<String> names(String src) {
        return ts(src).stream().map(OutlineModel.Item::name).toList();
    }

    @Test
    @DisplayName("a routes file outlines as its route table, targets and all")
    void routesFileOutlinesAsItsRouteTable() {
        // the shape that produced ZERO items before this release
        List<OutlineModel.Item> items = ts("""
                import { Routes } from '@angular/router';
                import { HomeComponent } from './home/home.component';

                export const routes: Routes = [
                  { path: '', component: HomeComponent },
                  { path: 'heroes', loadComponent: () => import('./heroes/heroes.component').then(m => m.HeroesComponent) },
                  {
                    path: 'admin',
                    loadChildren: () => import('./admin/admin.routes').then(m => m.ADMIN_ROUTES),
                  },
                  { path: '**', redirectTo: '' },
                ];
                """);

        assertThat(items.stream().map(OutlineModel.Item::name))
                .as("a path reads with the leading slash a developer says out"
                        + " loud; '' is the default route and '**' the wildcard")
                .containsExactly("/", "/heroes", "/admin", "/**");
        assertThat(items.stream().map(OutlineModel.Item::detail))
                .as("the target is what the route resolves to — eager"
                        + " component, lazy symbol, or redirect")
                .containsExactly("HomeComponent", "lazy HeroesComponent",
                        "lazy ADMIN_ROUTES", "→ /");
        assertThat(items).allSatisfy(i ->
                assertThat(i.kind()).isEqualTo(OutlineKind.TARGET));
        assertThat(items.get(2).line())
                .as("clicking /admin must land on the line that declares its"
                        + " path (7), not on the object's opening brace (6)")
                .isEqualTo(7);
    }

    @Test
    @DisplayName("children nest under their parent, in either writing style")
    void childrenNestUnderTheirParent() {
        // The two styles must agree about depth. A single-line route opens
        // its own brace; a multi-line one opened it on an earlier line, so
        // the raw brace depth is one deeper. Without the normalization, these
        // two siblings would render at different levels and /users would nest
        // under the WRONG parent.
        List<OutlineModel.Item> items = ts("""
                import { Routes } from '@angular/router';
                export const routes: Routes = [
                  { path: 'dash', component: DashComponent },
                  {
                    path: 'admin',
                    component: AdminComponent,
                    children: [
                      { path: 'users', component: UsersComponent },
                    ],
                  },
                ];
                """);

        assertThat(items.stream().map(OutlineModel.Item::name))
                .containsExactly("/dash", "/admin", "/users");
        assertThat(items.stream().map(OutlineModel.Item::depth))
                .as("both top-level routes sit at the same depth however they"
                        + " are written, and a child sits one deeper")
                .containsExactly(0, 0, 1);
        assertThat(items.get(1).detail())
                .as("a multi-line route's target is found on a later line of"
                        + " its own object")
                .isEqualTo("AdminComponent");
    }

    @Test
    @DisplayName("only a real route table — a component that merely imports the router is left alone")
    void onlyARealRouteTable() {
        // THE narrow-rule test. Importing @angular/router is what half the
        // components in a workspace do (RouterLink, Router, ActivatedRoute),
        // and a `path:` key is an ordinary thing to write. Either signal
        // alone must NOT arm the rule — only both together.
        assertThat(names("""
                import { RouterLink } from '@angular/router';
                const crumb = { path: 'heroes', label: 'Heroes' };
                export class NavComponent {}
                """))
                .as("an ordinary component with a path-shaped object is not a"
                        + " route table")
                .containsExactly("NavComponent");

        assertThat(names("""
                const config: Routes = [{ path: 'a', component: A }];
                """))
                .as("a Routes annotation with no @angular/router import is not"
                        + " Angular's Routes")
                .isEmpty();
    }

    @Test
    @DisplayName("a commented-out route is not a route")
    void commentsAreNotRoutes() {
        assertThat(names("""
                import { Routes } from '@angular/router';
                export const routes: Routes = [
                  // { path: 'old', component: OldComponent },
                  /* { path: 'older', component: OlderComponent }, */
                  { path: 'real', component: RealComponent },
                ];
                """))
                .as("routes ride the same stripNonCode guard as every other"
                        + " pattern in this extractor")
                .containsExactly("/real");
    }

    @Test
    @DisplayName("a commented-out target does not become the route's target")
    void commentedTargetIsNotATarget() {
        List<OutlineModel.Item> items = ts("""
                import { Routes } from '@angular/router';
                export const routes: Routes = [
                  {
                    path: 'admin',
                    // component: OldAdminComponent,
                    component: AdminComponent,
                  },
                ];
                """);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).detail())
                .as("the lookahead reads stripped code, not raw lines")
                .isEqualTo("AdminComponent");
    }

    @Test
    @DisplayName("a path key inside a data object is not a route (v1.316.0 review find)")
    void nestedDataPathIsNotARoute() {
        // Found by probe during the arc review: `data:` objects commonly
        // carry breadcrumbs, titles, canonical paths — and a `path:` key in
        // one, written on its own line, listed as a phantom child route.
        // The structural truth the fix leans on: a route object is always an
        // ARRAY element ([ or , precedes its brace), never the `key: {`
        // value object of some other key — so a path inside any :-opened
        // brace is data, not a route.
        List<OutlineModel.Item> items = ts("""
                import { Routes } from '@angular/router';
                export const routes: Routes = [
                  {
                    path: 'seo',
                    component: SeoComponent,
                    data: {
                      path: '/canonical',
                      breadcrumb: 'SEO',
                    },
                  },
                  { path: 'next', component: NextComponent },
                ];
                """);
        assertThat(items.stream().map(OutlineModel.Item::name))
                .as("the data object's path key must not list, and the route"
                        + " AFTER the data object must still list — the"
                        + " suppression state has to unwind when the brace"
                        + " closes")
                .containsExactly("/seo", "/next");
    }

    @Test
    @DisplayName("guards and helpers in a routes file still outline")
    void classicShapesUnaffected() {
        assertThat(names("""
                import { Routes } from '@angular/router';
                export function authGuard() { return true; }
                export const routes: Routes = [
                  { path: 'admin', component: AdminComponent },
                ];
                """))
                .as("the route rule must not shadow what already worked")
                .containsExactly("authGuard", "/admin");
    }
}
