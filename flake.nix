{ inputs =
    { nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
      utils.url = "github:ursi/flake-utils/8";
    };

  outputs = { utils, ... }@inputs:
    with builtins;
    utils.apply-systems { inherit inputs; }
      ({ pkgs, ... }:
         let l = p.lib; p = pkgs; in
         { packages.default =
             p.stdenv.mkDerivation
               { pname = "hexgui";
                 version = "0.10.GIT";
                 src = ./.;
                 nativeBuildInputs = with p; [ ant jdk makeWrapper ];
                 buildPhase = "ant";

                 installPhase =
                   ''
                   mkdir $out;
                   cp -r bin lib $out
                   wrapProgram $out/bin/hexgui --prefix PATH : ${p.jdk}/bin
                   '';

                 meta =
                   { description = "HexGui";
                     longDescription = "GUI for the board game Hex (and Y)";
                   };
               };

           devShells.default =
             p.mkShell
               { packages =
                   with p;
                   [ ant
                     jdk
                   ];
               };
         }
      );
}
