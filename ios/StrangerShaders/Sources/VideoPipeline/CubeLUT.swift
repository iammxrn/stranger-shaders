import Foundation
import Metal

nonisolated struct LUTPreset: Identifiable, Hashable {
  let name: String
  let url: URL

  var id: URL {
    url
  }

  static func bundled() -> [LUTPreset] {
    let urls = Bundle.main.urls(forResourcesWithExtension: "cube", subdirectory: nil) ?? []
    return urls
      .map { LUTPreset(name: CubeLUT.peekTitle(of: $0) ?? $0.deletingPathExtension().lastPathComponent, url: $0) }
      .sorted { $0.name < $1.name }
  }
}

nonisolated struct CubeLUT {
  enum ParseError: Error {
    case missingSize
    case malformedLine(String)
    case entryCountMismatch(expected: Int, got: Int)
  }

  let size: Int
  /// RGBA16F entries, red index varies fastest. That matches both the .cube
  /// file order and the x-fastest memory layout of a 3D Metal texture.
  let entries: [Float16]

  static func peekTitle(of url: URL) -> String? {
    guard let handle = try? FileHandle(forReadingFrom: url),
          let data = try? handle.read(upToCount: 512),
          let head = String(data: data, encoding: .utf8) else { return nil }
    for line in head.split(separator: "\n") {
      let trimmed = line.trimmingCharacters(in: .whitespaces)
      if trimmed.hasPrefix("TITLE") {
        return trimmed
          .dropFirst("TITLE".count)
          .trimmingCharacters(in: CharacterSet(charactersIn: " \"\r"))
      }
    }
    return nil
  }

  static func parse(contentsOf url: URL) throws -> CubeLUT {
    let text = try String(contentsOf: url, encoding: .utf8)
    var size = 0
    var entries: [Float16] = []
    for rawLine in text.split(separator: "\n") {
      let line = rawLine.trimmingCharacters(in: .whitespaces)
      if line.isEmpty || line.hasPrefix("#") { continue }
      if line.hasPrefix("TITLE") || line.hasPrefix("DOMAIN_MIN") || line.hasPrefix("DOMAIN_MAX") { continue }
      if line.hasPrefix("LUT_3D_SIZE") {
        size = Int(line.dropFirst("LUT_3D_SIZE".count).trimmingCharacters(in: .whitespaces)) ?? 0
        entries.reserveCapacity(size * size * size * 4)
        continue
      }
      let components = line.split(separator: " ").compactMap { Float($0) }
      guard components.count == 3 else { throw ParseError.malformedLine(line) }
      entries.append(Float16(components[0]))
      entries.append(Float16(components[1]))
      entries.append(Float16(components[2]))
      entries.append(1)
    }
    guard size > 1 else { throw ParseError.missingSize }
    guard entries.count == size * size * size * 4 else {
      throw ParseError.entryCountMismatch(expected: size * size * size * 4, got: entries.count)
    }
    return CubeLUT(size: size, entries: entries)
  }

  func makeTexture(device: MTLDevice) -> MTLTexture? {
    let descriptor = MTLTextureDescriptor()
    descriptor.textureType = .type3D
    descriptor.pixelFormat = .rgba16Float
    descriptor.width = size
    descriptor.height = size
    descriptor.depth = size
    descriptor.usage = .shaderRead
    guard let texture = device.makeTexture(descriptor: descriptor) else { return nil }
    let bytesPerRow = size * 4 * MemoryLayout<Float16>.size
    entries.withUnsafeBytes { bytes in
      texture.replace(
        region: MTLRegionMake3D(0, 0, 0, size, size, size),
        mipmapLevel: 0,
        slice: 0,
        withBytes: bytes.baseAddress!,
        bytesPerRow: bytesPerRow,
        bytesPerImage: bytesPerRow * size
      )
    }
    return texture
  }

  static func identity(device: MTLDevice) -> MTLTexture? {
    // A 2×2×2 ramp is an exact identity under trilinear interpolation.
    var entries: [Float16] = []
    for b in 0...1 {
      for g in 0...1 {
        for r in 0...1 {
          entries += [Float16(r), Float16(g), Float16(b), 1]
        }
      }
    }
    return CubeLUT(size: 2, entries: entries).makeTexture(device: device)
  }
}
