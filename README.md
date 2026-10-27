# Thnorty patches

The official [Morphe Patches](https://github.com/MorpheApp/morphe-patches) with additional patches.
Every patch of the official bundle is included, so this source replaces it.

## Additional patches

| App | Patch | Description |
|---|---|---|
| Reddit | Hide low score posts | Hides posts in subreddit feeds below a minimum score, set per subreddit or as a default, with a rules list in Morphe settings. Home and Popular are not filtered. |

## Use in Morphe

Add `github.com/Thnorty/Thnorty-morphe-patches` as a source in Morphe (**Sources** → **+** → **Remote**),
then disable the official Morphe Patches source for the apps you patch with this one.

Turn on **Pre-release patches** for this source to follow the official pre-releases.

## How it is built

- The `features` branch holds the additional commits, based on an official release.
- `sync.sh`, run by the [sync workflow](.github/workflows/sync.yml) every 6 hours, replays them onto
  the newest official release (`main`, stable) and pre-release (`dev`), runs the tests, builds the
  bundle and publishes it as a release of this repository.
- Versions are `<official version>-thnorty.<features commit>`.

If the features conflict with a new official release, the workflow fails and the previous build
stays available until the `features` branch is rebased.

## License

GNU General Public License v3.0 with the additional GPLv3 Section 7 conditions of Morphe Patches,
see the [LICENSE](https://github.com/MorpheApp/morphe-patches/blob/main/LICENSE) and
[NOTICE](https://github.com/MorpheApp/morphe-patches/blob/main/NOTICE). This project is not
affiliated with Morphe.
