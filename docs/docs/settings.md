# Settings

The settings of the plugin live under *Tools → MkDocs*. That page holds nothing itself — every setting
belongs to one feature of a site, and each feature carries a page of its own below it. Today there is one:
*Material*. Its setting belongs to the project rather than to the IDE, because it points into the environment
of *this* project.

## Material

### Installation directory

The icons of *Material for MkDocs* are not a list the plugin carries — they are the SVG files shipped inside
the installed package, so which ones exist depends on the version that is installed. Where that package lies
is asked of pip itself: the plugin runs `pip show mkdocs-material` and reads the `Location` it reports —
the `site-packages` directory holding the package and the `mkdocs_material-*.dist-info` beside it. Nothing
else is searched — no directory of the checkout is guessed at, because pip knows where the packages of the
interpreter in use lie. The icons are then read out of `material/templates/.icons` below it, and their names
out of the `RECORD` the installation itself wrote.

The page shows a fixed list: the installation that was found, named by its directory rather than calling
itself a default — once, as that entry, and never a second time below itself — every further installation pip
reports, and one entry for a directory of your own. Leave the found one selected while pip
answers for the interpreter the site is built with. Pick the last entry for every other setup — an
interpreter pip does not answer for, a system wide installation, a directory mounted from a container; only
then does the field below the list become editable, and the button next to it opens a directory chooser.

A directory chosen that way is checked before it is accepted: it has to hold a `mkdocs_material-*.dist-info`
whose `METADATA` names `mkdocs-material` and whose `RECORD` can be read. What is wrong with it is stated in
red below the fields, and the page refuses to apply until it is right — an unchecked path would show itself
as an empty completion popup and nothing else.

Otherwise that line states which directory the icons are actually read from: a configured installation wins
over the automatic entry, and a line saying that nothing is read is the answer to a completion popup offering
no icons.

!!! note

    A configured path that is no installation any more falls back to what pip reports rather than switching
    the icons off, so a moved environment degrades into the default behaviour instead of into silence. While
    nothing can be found at all, `mkdocs.yml` of a Material site carries a banner saying so, with a link to
    this page.

Applying the page throws the icon index away, so a corrected path takes effect in the next completion popup
rather than after a restart. The index is also refreshed on its own whenever something below a
`site-packages` directory changes — installing or upgrading `mkdocs-material` while the IDE is open is
enough.
