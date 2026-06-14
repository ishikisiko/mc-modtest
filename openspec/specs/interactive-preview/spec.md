# Interactive Preview

## Purpose

This spec captures the offline interactive 3D preview generated alongside structure preview PNGs.

## Requirements

### Requirement: Offline self-contained 3D viewer output

The preview tool SHALL emit, for each rendered structure, a single `viewer.html` file at `out/preview/<stem>/viewer.html` that renders the structure as an interactive 3D model. The file MUST open and function correctly by double-click in a browser with no network access: all voxel data, the color palette, and the rendering library MUST be available locally (inline data plus a repo-vendored `three.min.js` loaded by relative path).

#### Scenario: Render a single structure to a viewer

- **WHEN** a user runs the preview tool on a `.nbt` or structure `.json` file
- **THEN** the tool writes `out/preview/<stem>/viewer.html` alongside the existing PNG outputs
- **AND** the viewer displays the structure as 3D voxel cubes colored from the same block-color resolution used by the PNG outputs

#### Scenario: Works fully offline

- **WHEN** the generated `viewer.html` is opened with no internet connection
- **THEN** the 3D model renders and all interactions function, with no external network requests

#### Scenario: Batch mode emits one viewer per structure

- **WHEN** the tool is run in `--all` mode over a directory of structures
- **THEN** it writes one `out/preview/<stem>/viewer.html` per structure, matching the existing per-stem output layout
- **AND** when more than one viewer is written, it writes `out/preview/index.html` as an aggregate review entry point

### Requirement: Camera orbit, zoom, and pan

The viewer SHALL let the user freely rotate, zoom, and pan the camera around the structure.

#### Scenario: Orbit the model

- **WHEN** the user drags or scrolls in the viewer
- **THEN** the camera orbits, zooms, or pans around the structure, allowing inspection from any angle

### Requirement: Layer and block visibility toggles

The viewer SHALL let the user show or hide voxels by Y-layer and by block base id, so individual floors or material types can be isolated.

#### Scenario: Isolate a floor by Y-layer

- **WHEN** the user restricts the visible Y-layer range
- **THEN** only voxels within that range remain visible and the rest are hidden

#### Scenario: Toggle a block type

- **WHEN** the user toggles a block base id off
- **THEN** all voxels of that block base id are hidden and the remaining model stays visible

### Requirement: Draggable cross-section plane

The viewer SHALL provide a movable cross-section control that hides all voxels past a plane along a chosen axis (X, Y, or Z), producing a solid cut face. The voxel data MUST retain interior (non-exposed) voxels so the cut reveals a filled cross-section rather than a hollow shell.

#### Scenario: Move the cross-section along an axis

- **WHEN** the user drags the cross-section control along the selected axis
- **THEN** voxels beyond the plane are hidden in real time, revealing the interior at that depth as a solid cut face

#### Scenario: Cross-section combines with orbit

- **WHEN** a cross-section is active
- **THEN** the user can still orbit, zoom, and pan to inspect the exposed cut from any angle

### Requirement: Additive, non-breaking output

Adding the viewer SHALL NOT alter or remove the existing PNG outputs (per-Y slices, contact sheet, isometric overview, legend). The viewer MUST remain a coarse voxel-color preview; it MUST NOT claim to represent blockstate detail such as door facing, stair direction, or trapdoor open/closed state.

#### Scenario: Existing PNG outputs unchanged

- **WHEN** the tool runs with default options
- **THEN** the slice, contact sheet, isometric, and legend PNGs are produced as before, and the viewer is written in addition to them

#### Scenario: Blockstate caveat preserved

- **WHEN** documentation describes the viewer
- **THEN** it states that blockstate detail still requires an in-game `/place template` check
