"""Import the authored right-hand gauntlet, including its pixel atlas, as a NeoForge OBJ."""

from __future__ import annotations

import json
import math
from pathlib import Path
import struct


def read_source(path: Path) -> tuple[list[dict], bytes]:
    """Read the embedded, untransformed triangle primitives used by the gauntlet authoring tool."""
    source = path.read_bytes()
    magic, version, length = struct.unpack_from("<4sII", source)
    if (magic, version, length) != (b"glTF", 2, len(source)):
        raise ValueError("Expected a complete GLB 2 gauntlet source")
    json_size, json_tag = struct.unpack_from("<I4s", source, 12)
    bin_size, bin_tag = struct.unpack_from("<I4s", source, 20 + json_size)
    if json_tag != b"JSON" or bin_tag != b"BIN\0" or 28 + json_size + bin_size != len(source):
        raise ValueError("Invalid gauntlet GLB chunks")
    doc = json.loads(source[20:20 + json_size])
    binary = memoryview(source)[28 + json_size:]

    def view(index: int) -> memoryview:
        entry = doc["bufferViews"][index]
        start = entry.get("byteOffset", 0)
        end = start + entry["byteLength"]
        if entry["buffer"] != 0 or not 0 <= start < end <= len(binary):
            raise ValueError("Gauntlet buffer view outside embedded storage")
        if "byteStride" in entry:
            raise ValueError("Interleaved gauntlet attributes are unsupported")
        return binary[start:end]

    def read(index: int, kind: str, component: int) -> list[tuple]:
        entry = doc["accessors"][index]
        if entry["type"] != kind or entry["componentType"] != component or "sparse" in entry:
            raise ValueError("Unexpected gauntlet accessor format")
        width = {"SCALAR": 1, "VEC2": 2, "VEC3": 3}[kind]
        element = struct.Struct("<" + {5123: "H", 5126: "f"}[component] * width)
        start = entry.get("byteOffset", 0)
        size = entry["count"] * element.size
        buf = view(entry["bufferView"])
        if not 0 <= start < start + size <= len(buf):
            raise ValueError("Gauntlet accessor outside its buffer view")
        return list(element.iter_unpack(buf[start:start + size]))

    atlas, = doc["images"]
    if atlas["mimeType"] != "image/png":
        raise ValueError("The gauntlet source must embed a PNG atlas")
    texture = view(atlas["bufferView"]).tobytes()
    if not texture.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("Gauntlet atlas is not a PNG")

    meshes = []
    nodes = doc["scenes"][doc.get("scene", 0)]["nodes"]
    if len(set(nodes)) != len(nodes):
        raise ValueError("A gauntlet part occurs more than once in the export scene")
    for node_index in nodes:
        node = doc["nodes"][node_index]
        if any(key in node for key in ("matrix", "translation", "rotation", "scale", "children")):
            raise ValueError("Bake gauntlet transforms into the source mesh")
        mesh = doc["meshes"][node["mesh"]]
        for draw in mesh["primitives"]:
            if draw.get("mode", 4) != 4:
                raise ValueError("Gauntlet faces must be triangles")
            material = doc["materials"][draw["material"]]["pbrMetallicRoughness"]
            if doc["textures"][material["baseColorTexture"]["index"]]["source"] != 0:
                raise ValueError("All gauntlet faces must use the embedded atlas")
            attrs = draw["attributes"]
            vertices = read(attrs["POSITION"], "VEC3", 5126)
            normals = read(attrs["NORMAL"], "VEC3", 5126)
            uvs = read(attrs["TEXCOORD_0"], "VEC2", 5126)
            indices = [i for i, in read(draw["indices"], "SCALAR", 5123)]
            if not len(vertices) == len(normals) == len(uvs):
                raise ValueError("Mismatched gauntlet vertex attributes")
            if len(indices) % 3 or max(indices) >= len(vertices):
                raise ValueError("Invalid gauntlet triangles")
            if any(not math.isfinite(value) for points in (vertices, normals, uvs)
                   for point in points for value in point):
                raise ValueError("Non-finite gauntlet vertex data")
            if any(not 0 <= value <= 1 for uv in uvs for value in uv):
                raise ValueError("Gauntlet UV outside its atlas")
            meshes.append({
                "part": mesh["name"], "material": doc["materials"][draw["material"]]["name"],
                "vertices": vertices, "normals": normals, "uvs": uvs, "indices": indices
            })
    if not meshes:
        raise ValueError("Empty gauntlet scene")
    return meshes, texture


def model_point(position: tuple) -> tuple[float, float, float]:
    """Map centred preview (outer, punch, dorsal) axes to the existing item frame."""
    x, y, z = position
    return 8 - z, 8 + y, 8 + x


def export_obj(meshes: list[dict], model: str, texture: str, glow_texture: str | None = None) -> tuple[bytes, bytes]:
    lines = ["# Generated from the authored gauntlet GLB.", f"mtllib {model}.mtl"]
    offset = 1
    for i, mesh in enumerate(meshes):
        lines.extend((f"g part_{i}", f'# {mesh["part"]}', "usemtl heat" if mesh.get("glow") else "usemtl gauntlet"))
        for position in mesh["vertices"]:
            lines.append("v " + " ".join(f"{component / 16:.9g}" for component in model_point(position)))
        for u, v in mesh["uvs"]:
            lines.append(f"vt {u:.9g} {v:.9g}")
        for x, y, z in mesh["normals"]:
            lines.append(f"vn {-z:.9g} {y:.9g} {x:.9g}")
        for start in range(0, len(mesh["indices"]), 3):
            indices = [index + offset for index in mesh["indices"][start:start + 3]]
            # NeoForge bakes triangle faces into quads; use the same duplicate corner in the authored export.
            indices.append(indices[-1])
            lines.append("f " + " ".join(f"{index}/{index}/{index}" for index in indices))
        offset += len(mesh["vertices"])
    mtl = f"newmtl gauntlet\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd {texture}\n"
    if glow_texture is not None:
        mtl += f"\nnewmtl heat\nKd 1 1 1\nKa 1 1 1\nd 1\nmap_Kd {glow_texture}\n"
    return ("\n".join(lines) + "\n").encode(), mtl.encode()
